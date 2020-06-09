#include "MusicLibrary/MusicLibrary.h"
#include "Config/AppConfig.h"
#include "Config/AppState.h"
#include <QDataStream>
#include <QDirIterator>
#include <QtConcurrent/QtConcurrentRun>
#include <assert.h>
#include <windows.h>

MusicLibrary* MusicLibrary::s_Singleton = nullptr;

MusicLibrary::MusicLibrary()
{
  s_Singleton = this;

  AddSupportedFileExtension("mp3");
  AddSupportedFileExtension("mp4");
  AddSupportedFileExtension("m4a");
}

MusicLibrary::~MusicLibrary()
{
  Shutdown();

  s_Singleton = nullptr;
}

static void SqliteToUpper(sqlite3_context* context, int argc, sqlite3_value** argv)
{
  if (argc == 1)
  {
    const char* text = reinterpret_cast<const char*>(sqlite3_value_text(argv[0]));

    if (text && text[0])
    {
      const QString sText = QString::fromUtf8(text).toUpper();

      sqlite3_result_text(context, sText.toUtf8().data(), -1, SQLITE_TRANSIENT);
      return;
    }
  }

  sqlite3_result_null(context);
}

void MusicLibrary::Startup(const QString& sAppDir)
{
  Shutdown();

  const QString sDatabase = sAppDir + "/library.db";

  if (sqlite3_open(sDatabase.toUtf8().data(), &m_pSongDatabase) != 0)
  {
    int i = 0;
    (void)i;
  }

  if (!CreateTable())
  {
    // version changed
    Shutdown();

    if (QFile::remove(sDatabase))
    {
      Startup(sAppDir);
    }
  }

  connect(AppState::GetSingleton(), &AppState::BusyWorkActive, this, &MusicLibrary::onBusyWorkChanged);
  connect(AppConfig::GetSingleton(), &AppConfig::ProfileDirectoryChanged, this, &MusicLibrary::onProfileDirectoryChanged);

  sqlite3_create_function(m_pSongDatabase, "UPPER", 1, SQLITE_UTF8 | SQLITE_DETERMINISTIC, nullptr, SqliteToUpper, nullptr, nullptr);
}

void MusicLibrary::Shutdown()
{
  SaveUserState();

  m_bWorkersActive = false;

  if (m_WorkerTask.isRunning())
  {
    m_WorkerTask.waitForFinished();
  }

  if (m_pSongDatabase != nullptr)
  {
    sqlite3_close_v2(m_pSongDatabase);
  }

  m_pSongDatabase = nullptr;
}

void MusicLibrary::SaveUserState()
{
  std::lock_guard<std::mutex> lock(m_RecorderMutex);

  if (!m_Recorder.m_bRecordedModifcations)
    return;

  {
    const QString dt = QDateTime::currentDateTimeUtc().toString("yyyy-MM-dd-hh-mm-ss");
    const QString sDir = AppConfig::GetSingleton()->GetProfileDirectory() + "/library/";
    const QString sLibFile = sDir + dt + ".f1l";

    QDir().mkdir(sDir);

    QFile file(sLibFile);
    if (!file.open(QIODevice::OpenModeFlag::WriteOnly))
      return;

    QDataStream stream(&file);

    m_Recorder.CoalesceEntries();
    m_Recorder.Save(stream);
  }

  for (const QString& s : m_LibFilesToDeleteOnSave)
  {
    QFile::remove(s);
  }

  m_LibFilesToDeleteOnSave.clear();
}

void MusicLibrary::LoadUserState()
{
  const QString sDir = AppConfig::GetSingleton()->GetProfileDirectory() + "/library/";
  QDir().mkpath(sDir);

  QDirIterator dirIt(sDir, QDirIterator::Subdirectories | QDirIterator::FollowSymlinks);

  bool bMoreThanOneFile = false;
  while (dirIt.hasNext())
  {
    if (!m_bWorkersActive)
      return;

    dirIt.next();

    const QFileInfo fileInfo = dirIt.fileInfo();

    if (fileInfo.isDir())
      continue;

    const QString sLibraryFile = fileInfo.absoluteFilePath();

    if (!sLibraryFile.endsWith(".f1l", Qt::CaseInsensitive))
      continue;

    LoadLibraryFile(sLibraryFile);

    if (bMoreThanOneFile)
    {
      m_Recorder.m_bRecordedModifcations = true;
    }

    bMoreThanOneFile = true;
  }

  // using a transaction to update the DB in one go speeds this up by a huge factor
  SqlExec("BEGIN IMMEDIATE TRANSACTION", nullptr, nullptr);

  std::lock_guard<std::mutex> lock(m_RecorderMutex);
  m_Recorder.ApplyAll(this);

  SqlExec("END TRANSACTION", nullptr, nullptr);
}

void MusicLibrary::LoadLibraryFile(const QString& sPath)
{
  QFile file(sPath);
  if (!file.open(QIODevice::OpenModeFlag::ReadOnly))
    return;

  std::lock_guard<std::mutex> lock(m_RecorderMutex);

  m_LibFilesToDeleteOnSave.push_back(sPath);

  QDataStream stream(&file);

  m_Recorder.LoadAdditional(stream);
}

void MusicLibrary::onBusyWorkChanged(bool active)
{
  if (active == false)
  {
    m_bWorkersActive = true;
    m_WorkerTask = QtConcurrent::run(this, &MusicLibrary::CleanupThread);
  }
  else
  {
    m_bWorkersActive = false;

    if (m_WorkerTask.isRunning())
    {
      m_WorkerTask.waitForFinished();
    }
  }
}

void MusicLibrary::onProfileDirectoryChanged()
{
  {
    std::lock_guard<std::mutex> lock(m_RecorderMutex);

    // do not delete the files in the previous directory
    m_LibFilesToDeleteOnSave.clear();

    // save the state to the new directory now
    m_Recorder.m_bRecordedModifcations = true;
  }

  SaveUserState();
}

void MusicLibrary::SqlExec(const QString& stmt, int (*callback)(void*, int, char**, char**), void* userData) const
{
  char* szErrMsg = nullptr;
  auto ret = sqlite3_exec(m_pSongDatabase, stmt.toUtf8().data(), callback, userData, &szErrMsg);

  if (ret != SQLITE_OK && ret != SQLITE_ABORT)
  {
    char msg[512];
    sprintf_s(msg, 512, "SQL error: %s\n", szErrMsg);
    sqlite3_free(szErrMsg);

    OutputDebugStringA(msg);
  }
}

void MusicLibrary::CleanupThread()
{
  LoadUserState();

  // terminate the thread function as early as possible if m_bWorkersActive is set to false

  if (m_bWorkersActive)
  {
    UpdateSongPlayCount();
  }

  if (m_bWorkersActive)
  {
    RestoreFromDatabase();
  }

  if (m_bWorkersActive)
  {
    CleanUpLocations();
  }

  if (m_bWorkersActive)
  {
    CleanUpSongs();
  }

  m_bWorkersActive = false;
}

void MusicLibrary::SetSearchText(const QString& text)
{
  if (m_sSearchText == text)
    return;

  m_sSearchText = text;

  emit SearchTextChanged(m_sSearchText);
}

void MusicLibrary::AddSupportedFileExtension(const char* szExtension)
{
  QString sExt = szExtension;
  sExt = sExt.toLower();

  auto it = std::find(m_MusicFileExtensions.begin(), m_MusicFileExtensions.end(), sExt);

  if (it == m_MusicFileExtensions.end())
  {
    m_MusicFileExtensions.push_back(sExt);
  }
}

bool MusicLibrary::IsSupportedFileExtension(const char* szExtension) const
{
  QString sExt = szExtension;
  sExt = sExt.toLower();

  auto it = std::find(m_MusicFileExtensions.begin(), m_MusicFileExtensions.end(), sExt);

  return it != m_MusicFileExtensions.end();
}

inline static bool IsNull(const char* sz)
{
  return sz == nullptr;
}

static void RetrieveSongData(SongInfo& s, char** values)
{
  s.m_sSongGuid = values[0];
  s.m_sTitle = IsNull(values[1]) ? "<invalid>" : values[1];
  s.m_sArtist = IsNull(values[2]) ? "" : values[2];
  s.m_sAlbum = IsNull(values[3]) ? "" : values[3];
  s.m_iDiscNumber = IsNull(values[4]) ? 0 : QString(values[4]).toInt();
  s.m_iTrackNumber = IsNull(values[5]) ? 0 : QString(values[5]).toInt();
  s.m_iYear = IsNull(values[6]) ? 0 : QString(values[6]).toInt();
  s.m_iLengthInMS = IsNull(values[7]) ? 0 : QString(values[7]).toInt();
  s.m_iRating = QString(values[8]).toInt();
  s.m_iVolume = QString(values[9]).toInt();
  s.m_iStartOffset = QString(values[10]).toInt();
  s.m_iEndOffset = QString(values[11]).toInt();
  s.m_iLastPlayed = IsNull(values[12]) ? -1 : QString(values[12]).toInt();
  s.m_iDateAdded = IsNull(values[13]) ? -1 : QString(values[13]).toInt();
  s.m_iPlayCount = QString(values[14]).toInt();

  if (values[15] != nullptr)
    s.m_sLastPlayed = values[15];

  if (values[16] != nullptr)
    s.m_sDateAdded = values[16];
}

static int RetrieveSongArray(void* result, int numColumns, char** values, char** columnNames)
{
  SongInfo s;
  RetrieveSongData(s, values);

  std::deque<SongInfo>* allSongs = (std::deque<SongInfo>*)result;
  allSongs->push_back(s);
  return 0;
}

static int RetrieveSongGuidArray(void* result, int numColumns, char** values, char** columnNames)
{
  std::deque<QString>* allSongs = (std::deque<QString>*)result;
  allSongs->push_back(values[0]);
  return 0;
}

static int RetrieveSong(void* result, int numColumns, char** values, char** columnNames)
{
  SongInfo& s = *(SongInfo*)result;
  RetrieveSongData(s, values);
  return 0;
}

bool MusicLibrary::FindSong(const QString& songGuid, SongInfo& song) const
{
  std::lock_guard<std::mutex> lock(m_CacheMutex);

  for (size_t i = 0; i < m_songInfoCache.size(); ++i)
  {
    if (m_songInfoCache[i].m_sSongGuid == songGuid)
    {
      song = m_songInfoCache[i];
      return true;
    }
  }

  QString sql = QString("SELECT *" //id, title, artist, album, disc, track, year, length, rating, volume, start, end, lastplayed, dateadded, playcount"
                        ", strftime('%Y-%m-%d %H:%M', lastplayed, 'unixepoch', 'localtime') AS playedstring"
                        ", strftime('%Y-%m-%d %H:%M', dateadded, 'unixepoch', 'localtime') AS addedstring"
                        " FROM music WHERE id = '%1'")
                    .arg(songGuid);

  song.m_sSongGuid = QString();
  SqlExec(sql, RetrieveSong, &song);

  const bool bFound = !song.m_sSongGuid.isEmpty();
  song.m_sSongGuid = songGuid;

  if (bFound)
  {
    if (m_songInfoCache.size() > 31)
      m_songInfoCache.pop_front();

    m_songInfoCache.push_back(song);
  }

  return bFound;
}

std::deque<SongInfo> MusicLibrary::GetAllSongs(bool bUseSearchString) const
{
  std::deque<SongInfo> allSongs;

  if (m_pSongDatabase)
  {
    QString sql = "SELECT *"
                  ", strftime('%Y-%m-%d %H:%M', lastplayed, 'unixepoch', 'localtime') AS playedstring"
                  ", strftime('%Y-%m-%d %H:%M', dateadded, 'unixepoch', 'localtime') AS addedstring"
                  " FROM music ORDER BY artist, album, disc, track";

    if (bUseSearchString && !m_sSearchText.isEmpty())
    {
      char tmp[128];

      QStringList pieces = m_sSearchText.split(' ', QString::SkipEmptyParts);

      QString condition;

      for (const QString& piece : pieces)
      {
        if (!condition.isEmpty())
          condition.append(" AND ");

        sqlite3_snprintf(127, tmp, "%q", piece.toUtf8().data());
        condition.append(QString("(UPPER(title) LIKE UPPER('%%%1%%') OR UPPER(artist) LIKE UPPER('%%%1%%') OR UPPER(album) LIKE UPPER('%%%1%%'))").arg(tmp));
      }

      sql = QString("SELECT *"
                    ", strftime('%Y-%m-%d %H:%M', lastplayed, 'unixepoch', 'localtime') AS playedstring"
                    ", strftime('%Y-%m-%d %H:%M', dateadded, 'unixepoch', 'localtime') AS addedstring"
                    " FROM music WHERE %1"
                    " ORDER BY artist, album, disc, track")
                .arg(condition);
    }

    SqlExec(sql, RetrieveSongArray, &allSongs);
  }

  return std::move(allSongs);
}

std::deque<QString> MusicLibrary::GetAllSongGuids(bool bUseSearchString) const
{
  std::deque<QString> allSongs;

  if (m_pSongDatabase)
  {
    QString sql = "SELECT id FROM music";

    if (bUseSearchString && !m_sSearchText.isEmpty())
    {
      char tmp[128];

      QStringList pieces = m_sSearchText.split(' ', QString::SkipEmptyParts);

      QString condition;

      for (const QString& piece : pieces)
      {
        if (!condition.isEmpty())
          condition.append(" AND ");

        sqlite3_snprintf(127, tmp, "%q", piece.toUtf8().data());
        condition.append(QString("(UPPER(title) LIKE UPPER('%%%1%%') OR UPPER(artist) LIKE UPPER('%%%1%%') OR UPPER(album) LIKE UPPER('%%%1%%'))").arg(tmp));
      }

      sql = QString("SELECT id FROM music WHERE %1"
                    " ORDER BY artist, album, disc, track")
                .arg(condition);
    }

    SqlExec(sql, RetrieveSongGuidArray, &allSongs);
  }

  return std::move(allSongs);
}

std::deque<SongInfo> MusicLibrary::LookupSongs(const QString& where, const QString& orderBy) const
{
  std::deque<SongInfo> allSongs;

  if (m_pSongDatabase)
  {
    QString sql = QString("SELECT *"
                          ", strftime('%Y-%m-%d %H:%M', lastplayed, 'unixepoch', 'localtime') AS playedstring"
                          ", strftime('%Y-%m-%d %H:%M', dateadded, 'unixepoch', 'localtime') AS addedstring"
                          " FROM music");

    if (!where.isEmpty())
      sql += QString(" WHERE %1").arg(where);

    if (!orderBy.isEmpty())
      sql += QString(" ORDER BY %1").arg(orderBy);

    SqlExec(sql, RetrieveSongArray, &allSongs);
  }

  return std::move(allSongs);
}

void MusicLibrary::CountSongPlayed(const QString& sGuid)
{
  // set last play date (and increment counter)
  {
    QString sql = QString("UPDATE music SET lastplayed = (strftime('%s','now')), playcount = playcount + 1 WHERE id = '%1'")
                      .arg(sGuid);

    SqlExec(sql, nullptr, nullptr);
  }

  // read back current value
  SongInfo song;

  // record last play date
  if (FindSong(sGuid, song))
  {
    LibraryModification mod;
    mod.m_sSongGuid = sGuid;
    mod.m_Type = LibraryModification::Type::AddPlayDate;
    mod.m_iData = song.m_iLastPlayed;

    std::lock_guard<std::mutex> lock(m_RecorderMutex);

    m_Recorder.AddModification(mod, this);
  }
}

static int RetrieveTableVersion(void* result, int numColumns, char** values, char** columnNames)
{
  int* pVersion = (int*)result;

  *pVersion = QString(values[0]).toInt();
  return 0;
}

bool MusicLibrary::CreateTable()
{
  if (m_pSongDatabase == nullptr)
    return true;

  const int iCurrentVersion = 9;

  {
    const char* sql = "CREATE TABLE IF NOT EXISTS details (version INTEGER NOT NULL)";

    SqlExec(sql, nullptr, nullptr);

    int iTableVersion = -1;

    // check if there is a version number stored
    SqlExec("SELECT version FROM details", RetrieveTableVersion, &iTableVersion);

    if (iTableVersion == -1) // nothing stored
    {
      iTableVersion = iCurrentVersion;

      const QString qsql = QString("INSERT INTO details (version) VALUES('%1')").arg(iCurrentVersion);

      SqlExec(qsql, nullptr, nullptr);
    }

    if (iTableVersion != iCurrentVersion)
      return false;
  }

  {
    const char* sql = "CREATE TABLE IF NOT EXISTS music "
                      "(id TEXT NOT NULL"
                      ", title TEXT"
                      ", artist TEXT"
                      ", album TEXT"
                      ", disc INTEGER DEFAULT 0"
                      ", track INTEGER DEFAULT 0"
                      ", year INTEGER DEFAULT 0"
                      ", length INTEGER DEFAULT 0"
                      ", rating INTEGER DEFAULT 0"
                      ", volume INTEGER DEFAULT 0"
                      ", start INTEGER DEFAULT 0"
                      ", end INTEGER DEFAULT 0"
                      ", lastplayed INTEGER DEFAULT NULL"
                      ", dateadded INTEGER DEFAULT (strftime('%s','now'))"
                      ", playcount INTEGER DEFAULT 0"
                      ", PRIMARY KEY(id))";

    SqlExec(sql, nullptr, nullptr);
  }

  {
    const char* sql = "CREATE TABLE IF NOT EXISTS locations (path TEXT NOT NULL, id TEXT NOT NULL, modified TEXT, PRIMARY KEY(path))";

    SqlExec(sql, nullptr, nullptr);
  }

  return true;
}

void MusicLibrary::AddSongToLibrary(const QString& sGuid, const SongInfo& info)
{
  QString sql = QString("INSERT OR REPLACE INTO music (id, title, artist, album, disc, track, year, length) VALUES('%1'").arg(sGuid);

  char tmp[128];

  if (!info.m_sTitle.isEmpty())
  {
    sqlite3_snprintf(127, tmp, ", %Q", info.m_sTitle.toUtf8().data());
    sql += tmp;
  }
  else
    sql += ", NULL";

  if (!info.m_sArtist.isEmpty())
  {
    sqlite3_snprintf(127, tmp, ", %Q", info.m_sArtist.toUtf8().data());
    sql += tmp;
  }
  else
    sql += ", NULL";

  if (!info.m_sAlbum.isEmpty())
  {
    sqlite3_snprintf(127, tmp, ", %Q", info.m_sAlbum.toUtf8().data());
    sql += tmp;
  }
  else
    sql += ", NULL";

  sql += QString(", %1, %2, %3, %4)")
             .arg(info.m_iDiscNumber)
             .arg(info.m_iTrackNumber)
             .arg(info.m_iYear)
             .arg(info.m_iLengthInMS);

  SqlExec(sql, nullptr, nullptr);
}

void MusicLibrary::RemoveSongFromLibrary(const QString& sGuid)
{
  QString sql = QString("DELETE FROM music WHERE id = '%1'").arg(sGuid);

  SqlExec(sql, nullptr, nullptr);
}

void MusicLibrary::AddSongLocation(const QString& sGuid, const QString& sLocation, const QString& sLastModified)
{
  char tmp[256];
  sqlite3_snprintf(255, tmp, "%q", sLocation.toUtf8().data());

  QString sql = QString("INSERT OR REPLACE INTO locations (path, id, modified) VALUES('%1', '%2', '%3')")
                    .arg(tmp)
                    .arg(sGuid)
                    .arg(sLastModified);

  SqlExec(sql, nullptr, nullptr);
}

void MusicLibrary::RemoveSongLocation(const QString& sLocation)
{
  char tmp[256];
  sqlite3_snprintf(255, tmp, "%q", sLocation.toUtf8().data());

  QString sql = QString("DELETE FROM locations WHERE path = '%1'").arg(tmp);

  SqlExec(sql, nullptr, nullptr);
}

static int RetrieveSongLocation(void* result, int numColumns, char** values, char** columnNames)
{
  std::deque<QString>* locations = (std::deque<QString>*)result;

  locations->push_back(values[0]);
  return 0;
}

static int RetrieveHasSongLocation(void* result, int numColumns, char** values, char** columnNames)
{
  bool* bHasLocations = (bool*)result;

  *bHasLocations = true;
  return 1;
}

void MusicLibrary::GetSongLocations(const QString& sGuid, std::deque<QString>& out_Locations) const
{
  out_Locations.clear();

  QString sql = QString("SELECT path FROM locations WHERE id = '%1'")
                    .arg(sGuid);

  SqlExec(sql, RetrieveSongLocation, &out_Locations);
}

bool MusicLibrary::HasSongLocations(const QString& sGuid) const
{
  bool bHasLocations = false;

  QString sql = QString("SELECT path FROM locations WHERE id = '%1'")
                    .arg(sGuid);

  SqlExec(sql, RetrieveHasSongLocation, &bHasLocations);

  return bHasLocations;
}

bool MusicLibrary::IsLocationModified(const QString& sLocation, const QString& sLastModified) const
{
  char tmp[256];
  sqlite3_snprintf(255, tmp, "%q", sLocation.toUtf8().data());

  std::deque<QString> locations;

  QString sql = QString("SELECT path FROM locations WHERE path = '%1' AND modified = '%2'")
                    .arg(tmp)
                    .arg(sLastModified);

  SqlExec(sql, RetrieveSongLocation, &locations);

  return locations.empty();
}

void MusicLibrary::UpdateSongDuration(const QString& sGuid, int duration)
{
  QString sql = QString("UPDATE music SET length = %1 WHERE id = '%2'")
                    .arg(duration)
                    .arg(sGuid);

  SqlExec(sql, nullptr, nullptr);

  m_songInfoCache.clear();
}

void MusicLibrary::UpdateSongTitle(const QString& sGuid, const QString& value)
{
  char tmp[128];
  sqlite3_snprintf(127, tmp, "%q", value.toUtf8().data());

  QString sql = QString("UPDATE music SET title = '%1' WHERE id = '%2'")
                    .arg(tmp)
                    .arg(sGuid);

  SqlExec(sql, nullptr, nullptr);

  std::lock_guard<std::mutex> lock(m_CacheMutex);
  m_songInfoCache.clear();
}

void MusicLibrary::UpdateSongArtist(const QString& sGuid, const QString& value)
{
  char tmp[128];
  sqlite3_snprintf(127, tmp, "%q", value.toUtf8().data());

  QString sql = QString("UPDATE music SET artist = '%1' WHERE id = '%2'")
                    .arg(tmp)
                    .arg(sGuid);

  SqlExec(sql, nullptr, nullptr);

  std::lock_guard<std::mutex> lock(m_CacheMutex);
  m_songInfoCache.clear();
}

void MusicLibrary::UpdateSongAlbum(const QString& sGuid, const QString& value)
{
  char tmp[128];
  sqlite3_snprintf(127, tmp, "%q", value.toUtf8().data());

  QString sql = QString("UPDATE music SET album = '%1' WHERE id = '%2'")
                    .arg(tmp)
                    .arg(sGuid);

  SqlExec(sql, nullptr, nullptr);

  std::lock_guard<std::mutex> lock(m_CacheMutex);
  m_songInfoCache.clear();
}

void MusicLibrary::UpdateSongTrackNumber(const QString& sGuid, int value)
{
  QString sql = QString("UPDATE music SET track = %1 WHERE id = '%2'")
                    .arg(value)
                    .arg(sGuid);

  SqlExec(sql, nullptr, nullptr);

  std::lock_guard<std::mutex> lock(m_CacheMutex);
  m_songInfoCache.clear();
}

void MusicLibrary::UpdateSongDiscNumber(const QString& sGuid, int value, bool bRecord)
{
  if (bRecord)
  {
    LibraryModification mod;
    mod.m_sSongGuid = sGuid;
    mod.m_Type = LibraryModification::Type::SetDiscNumber;
    mod.m_iData = value;

    std::lock_guard<std::mutex> lock(m_RecorderMutex);

    m_Recorder.AddModification(mod, this);
  }
  else
  {
    QString sql = QString("UPDATE music SET disc = %1 WHERE id = '%2'")
                      .arg(value)
                      .arg(sGuid);

    SqlExec(sql, nullptr, nullptr);

    std::lock_guard<std::mutex> lock(m_CacheMutex);
    m_songInfoCache.clear();
  }
}

void MusicLibrary::UpdateSongYear(const QString& sGuid, int value)
{
  QString sql = QString("UPDATE music SET year = %1 WHERE id = '%2'")
                    .arg(value)
                    .arg(sGuid);

  SqlExec(sql, nullptr, nullptr);

  std::lock_guard<std::mutex> lock(m_CacheMutex);
  m_songInfoCache.clear();
}

void MusicLibrary::UpdateSongRating(const QString& sGuid, int value, bool bRecord)
{
  if (bRecord)
  {
    LibraryModification mod;
    mod.m_sSongGuid = sGuid;
    mod.m_Type = LibraryModification::Type::SetRating;
    mod.m_iData = value;

    std::lock_guard<std::mutex> lock(m_RecorderMutex);

    m_Recorder.AddModification(mod, this);
  }
  else
  {
    QString sql = QString("UPDATE music SET rating = %1 WHERE id = '%2'")
                      .arg(value)
                      .arg(sGuid);

    SqlExec(sql, nullptr, nullptr);

    std::lock_guard<std::mutex> lock(m_CacheMutex);
    m_songInfoCache.clear();
  }
}

void MusicLibrary::UpdateSongVolume(const QString& sGuid, int value, bool bRecord)
{
  if (bRecord)
  {
    LibraryModification mod;
    mod.m_sSongGuid = sGuid;
    mod.m_Type = LibraryModification::Type::SetVolume;
    mod.m_iData = value;

    std::lock_guard<std::mutex> lock(m_RecorderMutex);

    m_Recorder.AddModification(mod, this);
  }
  else
  {
    QString sql = QString("UPDATE music SET volume = %1 WHERE id = '%2'")
                      .arg(value)
                      .arg(sGuid);

    SqlExec(sql, nullptr, nullptr);

    std::lock_guard<std::mutex> lock(m_CacheMutex);
    m_songInfoCache.clear();
  }
}

void MusicLibrary::UpdateSongStartOffset(const QString& sGuid, int value, bool bRecord)
{
  if (bRecord)
  {
    LibraryModification mod;
    mod.m_sSongGuid = sGuid;
    mod.m_Type = LibraryModification::Type::SetStartOffset;
    mod.m_iData = value;

    std::lock_guard<std::mutex> lock(m_RecorderMutex);

    m_Recorder.AddModification(mod, this);
  }
  else
  {
    QString sql = QString("UPDATE music SET start = %1 WHERE id = '%2'")
                      .arg(value)
                      .arg(sGuid);

    SqlExec(sql, nullptr, nullptr);

    std::lock_guard<std::mutex> lock(m_CacheMutex);
    m_songInfoCache.clear();
  }
}

void MusicLibrary::UpdateSongEndOffset(const QString& sGuid, int value, bool bRecord)
{
  if (bRecord)
  {
    LibraryModification mod;
    mod.m_sSongGuid = sGuid;
    mod.m_Type = LibraryModification::Type::SetEndOffset;
    mod.m_iData = value;

    std::lock_guard<std::mutex> lock(m_RecorderMutex);

    m_Recorder.AddModification(mod, this);
  }
  else
  {
    QString sql = QString("UPDATE music SET end = %1 WHERE id = '%2'")
                      .arg(value)
                      .arg(sGuid);

    SqlExec(sql, nullptr, nullptr);

    std::lock_guard<std::mutex> lock(m_CacheMutex);
    m_songInfoCache.clear();
  }
}

void MusicLibrary::UpdateSongPlayDate(const QString& sGuid, int value)
{
  QString sql = QString("UPDATE music SET lastplayed = %1 WHERE id = '%2'")
                    .arg(value)
                    .arg(sGuid);

  SqlExec(sql, nullptr, nullptr);

  std::lock_guard<std::mutex> lock(m_CacheMutex);
  m_songInfoCache.clear();
}

void MusicLibrary::FindSongsInLocation(const QString& sLocationPrefix, std::deque<QString>& out_Guids) const
{
  if (!m_pSongDatabase)
    return;

  QString sql = QString("SELECT id FROM locations WHERE path LIKE '%1%%'")
                    .arg(sLocationPrefix);

  SqlExec(sql, RetrieveSongLocation, &out_Guids);
}

void MusicLibrary::RestoreFromDatabase()
{
  if (m_pSongDatabase == nullptr)
    return;

  const std::deque<SongInfo> allSongs = GetAllSongs(false);

  for (const SongInfo& si : allSongs)
  {
    if (!m_bWorkersActive)
      return;

    {
      std::lock_guard<std::mutex> lock(m_RecorderMutex);

      LibraryModification mod;
      mod.m_sSongGuid = si.m_sSongGuid;

      if (si.m_iRating != 0)
      {
        mod.m_Type = LibraryModification::Type::SetRating;
        mod.m_iData = si.m_iRating;
        m_Recorder.EnsureModificationExists(mod, this);
      }

      if (si.m_iDiscNumber != 0)
      {
        mod.m_Type = LibraryModification::Type::SetDiscNumber;
        mod.m_iData = si.m_iDiscNumber;
        m_Recorder.EnsureModificationExists(mod, this);
      }

      if (si.m_iVolume != 0)
      {
        mod.m_Type = LibraryModification::Type::SetVolume;
        mod.m_iData = si.m_iVolume;
        m_Recorder.EnsureModificationExists(mod, this);
      }

      if (si.m_iStartOffset != 0)
      {
        mod.m_Type = LibraryModification::Type::SetStartOffset;
        mod.m_iData = si.m_iStartOffset;
        m_Recorder.EnsureModificationExists(mod, this);
      }

      if (si.m_iEndOffset != 0)
      {
        mod.m_Type = LibraryModification::Type::SetEndOffset;
        mod.m_iData = si.m_iEndOffset;
        m_Recorder.EnsureModificationExists(mod, this);
      }
    }

    // don't hog the CPU with this too much, leave it running in the background
    Sleep(1);
  }
}

void MusicLibrary::CleanUpLocations()
{
  if (!m_pSongDatabase)
    return;

  std::deque<QString> allLocations;
  std::deque<QString> toRemove;

  SqlExec("SELECT path FROM locations", RetrieveSongLocation, &allLocations);

  for (const QString& loc : allLocations)
  {
    if (!m_bWorkersActive)
      return;

    if (!QFile::exists(loc))
    {
      toRemove.push_back(loc);
    }
  }

  // now remove all locations in one transaction
  if (!toRemove.empty())
  {
    SqlExec("BEGIN IMMEDIATE TRANSACTION", nullptr, nullptr);
    for (const QString& loc : toRemove)
    {
      RemoveSongLocation(loc);
    }
    SqlExec("END TRANSACTION", nullptr, nullptr);
  }
}

void MusicLibrary::CleanUpSongs()
{
  if (!m_pSongDatabase)
    return;

  std::deque<QString> allSongs;
  std::deque<QString> toRemove;

  SqlExec("SELECT id FROM music", RetrieveSongGuidArray, &allSongs);

  for (const QString& song : allSongs)
  {
    if (!m_bWorkersActive)
      return;

    if (!HasSongLocations(song))
    {
      toRemove.push_back(song);
    }

    // don't hog the CPU with this too much, leave it running in the background
    Sleep(1);
  }

  if (!toRemove.empty())
  {
    SqlExec("BEGIN IMMEDIATE TRANSACTION", nullptr, nullptr);
    for (const QString& song : toRemove)
    {
      RemoveSongFromLibrary(song);
    }
    SqlExec("END TRANSACTION", nullptr, nullptr);
  }
}

void MusicLibrary::UpdateSongPlayCount()
{
  if (!m_pSongDatabase)
    return;

  ModificationRecorder<LibraryModification, MusicLibrary*> recorder;

  // make a copy
  {
    std::lock_guard<std::mutex> lock(m_RecorderMutex);
    recorder = m_Recorder;
  }

  std::map<QString, int> infos;
  std::set<QString> entryCounted;

  for (const auto& rec : recorder.GetAllModifications())
  {
    if (!m_bWorkersActive)
      return;

    if (rec.m_Type != LibraryModification::Type::AddPlayDate)
      continue;

    // at this point, entries are not coalesced, so we must filter out duplicate play date information
    if (entryCounted.find(rec.m_sModGuid) != entryCounted.end())
      continue;

    entryCounted.insert(rec.m_sModGuid);

    infos[rec.m_sSongGuid]++;
  }

  // update database
  {
    SqlExec("BEGIN IMMEDIATE TRANSACTION", nullptr, nullptr);
    for (const auto itInfo : infos)
    {
      // no need to update lastplayed, that is already done during startup
      QString sql = QString("UPDATE music SET playcount = %1 WHERE id = '%2'")
                        .arg(itInfo.second)
                        .arg(itInfo.first);

      SqlExec(sql, nullptr, nullptr);
    }
    SqlExec("END TRANSACTION", nullptr, nullptr);
  }
}

void LibraryModification::Apply(MusicLibrary* pContext) const
{
  switch (m_Type)
  {
  case LibraryModification::Type::SetRating:
    pContext->UpdateSongRating(m_sSongGuid, m_iData, false);
    break;
  case LibraryModification::Type::SetVolume:
    pContext->UpdateSongVolume(m_sSongGuid, m_iData, false);
    break;
  case LibraryModification::Type::SetStartOffset:
    pContext->UpdateSongStartOffset(m_sSongGuid, m_iData, false);
    break;
  case LibraryModification::Type::SetEndOffset:
    pContext->UpdateSongEndOffset(m_sSongGuid, m_iData, false);
    break;
  case LibraryModification::Type::AddPlayDate:
    pContext->UpdateSongPlayDate(m_sSongGuid, m_iData);
    break;
  case LibraryModification::Type::SetDiscNumber:
    pContext->UpdateSongDiscNumber(m_sSongGuid, m_iData, false);
    break;

  default:
    assert(false && "not implemented");
  }
}

void LibraryModification::Save(QDataStream& stream) const
{
  stream << (int)m_Type;
  stream << m_sSongGuid;
  stream << m_iData;
}

void LibraryModification::Load(QDataStream& stream)
{
  int type;
  stream >> type;
  m_Type = (Type)type;
  stream >> m_sSongGuid;
  stream >> m_iData;
}

void LibraryModification::Coalesce(ModificationRecorder<LibraryModification, MusicLibrary*>& recorder, size_t passThroughIndex)
{
  switch (m_Type)
  {
  case Type::SetRating:
  case Type::SetVolume:
  case Type::SetStartOffset:
  case Type::SetEndOffset:
  case Type::SetDiscNumber:
  {
    // remove previous changes to the same song and same property

    recorder.InvalidatePrevious(passThroughIndex, [this](const LibraryModification& mod) -> bool {
      if (mod.m_sSongGuid == this->m_sSongGuid)
      {
        return mod.m_Type == this->m_Type;
      }

      return false;
    });
  }
  break;

  case Type::AddPlayDate:
    break;
  }
}
