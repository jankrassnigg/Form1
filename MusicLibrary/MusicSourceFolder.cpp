#include "MusicLibrary/MusicSourceFolder.h"
#include "Config/AppState.h"
#include "MusicLibrary/MusicLibrary.h"
#include "MusicLibrary/SortLibraryDlg.h"

#include <QCryptographicHash>
#include <QDateTime>
#include <QDirIterator>
#include <QProgressDialog>
#include <QtConcurrent/QtConcurrentRun>
#include <taglib/fileref.h>
#include <taglib/tag.h>

MusicSourceFolder::MusicSourceFolder(const QString& path)
{
  m_sFolder = path;
}

MusicSourceFolder::~MusicSourceFolder()
{
}

void MusicSourceFolder::Startup()
{
  m_bShutdownWorker = false;
  m_WorkerTask = QtConcurrent::run(this, &MusicSourceFolder::ParseFolder);
}

void MusicSourceFolder::Shutdown()
{
  if (m_WorkerTask.isRunning())
  {
    m_bShutdownWorker = true;
    m_WorkerTask.waitForFinished();
  }
}

void MusicSourceFolder::ParseFolder()
{
  AppState::GetSingleton()->BeginBusyWork();

  QDirIterator dirIt(m_sFolder, QDirIterator::Subdirectories | QDirIterator::FollowSymlinks);

  MusicLibrary* ml = MusicLibrary::GetSingleton();

  int imported = 0;

  while (!m_bShutdownWorker && dirIt.hasNext())
  {
    dirIt.next();

    if (UpdateFile(dirIt.fileInfo()))
    {
      ++imported;

      if (imported > 50)
      {
        imported = 0;
        AppState::GetSingleton()->SongsHaveBeenImported();
      }
    }
  }

  if (imported > 0)
    AppState::GetSingleton()->SongsHaveBeenImported();

  AppState::GetSingleton()->EndBusyWork();
}

bool MusicSourceFolder::UpdateFile(const QFileInfo& info)
{
  if (info.isDir())
    return false;

  MusicLibrary* ml = MusicLibrary::GetSingleton();

  if (!ml->IsSupportedFileExtension(info.suffix().toUtf8().data()))
    return false;

  const QString sLocation = info.absoluteFilePath();
  const QString sModDate = info.lastModified().toString("yyyy-MM-dd-hh-mm-ss");

  if (!ml->IsLocationModified(sLocation, sModDate))
    return false;

  SongInfo songInfo;
  songInfo.m_sSongGuid = ComputeFileGuid(sLocation);

  if (songInfo.m_sSongGuid.isEmpty())
    return false;

  songInfo.ReadSongInfo(sLocation);

  ml->AddSongToLibrary(songInfo.m_sSongGuid, songInfo);
  ml->AddSongLocation(songInfo.m_sSongGuid, sLocation, sModDate);

  if (songInfo.m_iDiscNumber != 0)
  {
    // disc number is not stored in the file tag itself, but externally
    ml->UpdateSongDiscNumber(songInfo.m_sSongGuid, songInfo.m_iDiscNumber, true);
  }

  return true;
}

// Skips ID3 tags only, thus only works for mp3 files!
static void AdjustRangeID3v2(QFile& file, qint64& rangeStart)
{
  char tag[3];
  if (file.read(tag, 3) != 3)
    return;

  if (tag[0] != 'I' || tag[1] != 'D' || tag[2] != '3')
    return;

  file.seek(3 + 2 + 1);

  char data[4];
  if (file.read(data, 4) != 4)
    return;

  qint32 size = ((qint32)data[0] << (7 * 3)) |
                ((qint32)data[1] << (7 * 2)) |
                ((qint32)data[2] << 7) |
                ((qint32)data[3]);

  rangeStart = 3 + 2 + 1 + 4 + size;
}

static void AdjustRangeID3v1(QFile& file, qint64& rangeEnd)
{
  if (!file.seek(file.size() - 128))
    return;

  char tag[3];
  if (file.read(tag, 3) != 3)
    return;

  if (tag[0] != 'T' || tag[1] != 'A' || tag[2] != 'G')
    return;

  rangeEnd = file.size() - 128;
}

QString MusicSourceFolder::ComputeFileGuid(const QString& sFilepath) const
{
  QFile file(sFilepath);

  if (!file.open(QIODevice::ReadOnly))
    return QString();

  // MP3 files can have tags at the end OR the beginning,
  // which really messes up the GUID computation when you modify metadata and the tag is changed
  // from being at the end to being at the beginning
  // therefore always skip the MP3 tag at the beginning, if we find it, because we know the file format for this

  qint64 rangeStart = 0;
  qint64 rangeEnd = file.size();
  AdjustRangeID3v2(file, rangeStart);
  AdjustRangeID3v1(file, rangeEnd);
  file.seek(rangeStart);

  const qint64 fileSize = rangeEnd - rangeStart;

  // TODO: if has ID3 tag at end -> ignore last 128 bytes

  const qint64 blockSize = 4096 * 4;
  const qint64 readRangeEnd = fileSize - blockSize;               // ignore the last part of the file, depending on the format, this may contain metadata, which can change
  const qint64 fullBlocks = ((readRangeEnd - 0) / blockSize) - 2; // how much of the file we want to read, -1 because we also want to skip the start of the file, which often contains metadata
  const qint64 readRangeStart = readRangeEnd - (fullBlocks * blockSize);

  // if the file is too small, we can't properly handle it
  if (readRangeStart < 0 || readRangeEnd < 0 || fullBlocks <= 0)
    return QString();

  QCryptographicHash hash(QCryptographicHash::Algorithm::Md5); // 128 bit should be sufficient for identification
  QDataStream in(&file);

  char buffer[blockSize * 3];

  // skip the first n bytes, which potentially contain the song description
  in.readRawData(buffer, readRangeStart);

  // now read and hash the number of full blocks that we computed before
  for (qint64 i = 0; i < fullBlocks; ++i)
  {
    in.readRawData(buffer, blockSize);

    hash.addData(buffer, blockSize);
  }

  return hash.result().toHex();
}

#include <QMessageBox>

QString MakeValidString(const QString& r, bool bFile)
{
  QString s = r.trimmed();

  // remove redundant extensions
  // TODO: could be done more data driven
  if (s.endsWith(".mp3", Qt::CaseInsensitive))
    s.chop(4);
  if (s.endsWith(".m4a", Qt::CaseInsensitive))
    s.chop(4);
  if (s.endsWith(".mp4", Qt::CaseInsensitive))
    s.chop(4);
  if (s.endsWith(".wav", Qt::CaseInsensitive))
    s.chop(4);
  if (s.endsWith(".ogg", Qt::CaseInsensitive))
    s.chop(4);

  s.replace('?', '_');
  s.replace(':', '_');
  s.replace('/', '_');
  s.replace('\\', '_');
  s.replace('*', '_');
  s.replace('<', '_');
  s.replace('>', '_');
  s.replace('|', '_');

  //s.replace('\'', "");
  //s.replace(',', "");
  //s.replace(';', "");
  s.replace('\"', "");
  s.replace('\n', "");
  s.replace('\t', "");
  s.replace('\r', "");

  if (bFile)
  {
    s.replace('.', "");
  }

  if (s.length() > 31)
    s.resize(31);

  return s.trimmed();
}

void MusicSourceFolder::GatherFilesToSort(const QString& folderPath, std::deque<CopyInfo>& cis)
{
  std::deque<QString> guids;
  std::set<QString> songsAlreadyFound;
  std::deque<QString> locations;
  MusicLibrary::GetSingleton()->FindSongsInLocation(folderPath, guids);

  QProgressDialog progress("Checking Files", "Cancel", 0, (int)guids.size(), nullptr);
  progress.setMinimumDuration(0);
  progress.setWindowModality(Qt::WindowModal);

  int prog = 0;
  for (const QString& guid : guids)
  {
    progress.setValue(++prog);

    if (progress.wasCanceled())
      return;

    if (songsAlreadyFound.find(guid) != songsAlreadyFound.end())
      continue;

    songsAlreadyFound.insert(guid);

    SongInfo info;
    MusicLibrary::GetSingleton()->FindSong(guid, info);
    MusicLibrary::GetSingleton()->GetSongLocations(guid, locations);

    if (locations.empty())
      continue;

    for (const QString& location : locations)
    {
      if (!location.startsWith(folderPath, Qt::CaseInsensitive))
        continue;

      const QFileInfo fi(location);

      if (!fi.exists())
      {
        MusicLibrary::GetSingleton()->RemoveSongLocation(location);
        continue;
      }

      const bool bHasAlbum = !info.m_sAlbum.isEmpty();

      const QString artist = info.m_sArtist.isEmpty() ? "Unknown Artist" : MakeValidString(info.m_sArtist, false);
      const QString album = bHasAlbum ? MakeValidString(info.m_sAlbum, false) : "Unknown Album";
      const QString disk = (bHasAlbum && info.m_iDiscNumber != 0) ? QString("%1-").arg(info.m_iDiscNumber) : QString();
      const QString track = (bHasAlbum && info.m_iTrackNumber != 0) ? QString("%1 ").arg(info.m_iTrackNumber, 2, 10, QChar('0')) : QString();
      const QString title = info.m_sTitle.isEmpty() ? "Unknown Title" : MakeValidString(info.m_sTitle, true);
      const QString ext = fi.suffix();

      QString newPath = QString("%1/%2/%3")
                            .arg(folderPath)
                            .arg(artist)
                            .arg(album);

      QString newFile = QString("%1/%2%3%4.%5")
                            .arg(newPath)
                            .arg(disk)
                            .arg(track)
                            .arg(title)
                            .arg(ext);

      if (newFile.compare(location, Qt::CaseInsensitive) == 0)
        continue;

      CopyInfo ci;
      ci.m_sGuid = guid;
      ci.m_sSource = location;
      ci.m_sTargetFolder = newPath;
      ci.m_sTargetFile = newFile;
      cis.push_back(ci);
    }
  }
}

bool MusicSourceFolder::ExecuteFileSort(const CopyInfo& ci, QString& outError)
{
  if (!QFileInfo(ci.m_sSource).exists())
  {
    outError = "Source file does not exist.";
    return false;
  }

  if (!QDir().mkpath(ci.m_sTargetFolder))
  {
    outError = "Could not make target path.";
    return false;
  }

  if (!QFileInfo(ci.m_sTargetFile).exists())
  {
    if (!QFile::copy(ci.m_sSource, ci.m_sTargetFile))
    {
      outError = "Could not copy source to target.";
      return false;
    }

    const QFileInfo targetInfo(ci.m_sTargetFile);
    const QString sModDate = targetInfo.lastModified().toString("yyyy-MM-dd-hh-mm-ss");

    MusicLibrary::GetSingleton()->AddSongLocation(ci.m_sGuid, ci.m_sTargetFile, sModDate);

    if (!QFile::remove(ci.m_sSource))
    {
      outError = "Could not delete source file.";
      return false;
    }

    MusicLibrary::GetSingleton()->RemoveSongLocation(ci.m_sSource);
  }
  else
  {
    QByteArray srcData, dstData;
    {
      QFile srcFile(ci.m_sSource);
      QFile dstFile(ci.m_sTargetFile);

      if (!srcFile.open(QIODevice::ReadOnly))
      {
        outError = "Could not open source file.";
        return false;
      }

      if (!dstFile.open(QIODevice::ReadOnly))
      {
        outError = "Could not open target file.";
        return false;
      }

      srcData = srcFile.readAll();
      dstData = dstFile.readAll();
    }

    if (srcData == dstData)
    {
      if (!QFile::remove(ci.m_sSource))
      {
        outError = "Could not delete source file.";
        return false;
      }

      MusicLibrary::GetSingleton()->RemoveSongLocation(ci.m_sSource);
    }
    else
    {
      outError = "Target file already exists with different content.";
      return false;
    }
  }

  return true;
}

void MusicSourceFolder::DeleteEmptyFolders(const QString& folder)
{
  QDirIterator it(folder, QDirIterator::IteratorFlag::Subdirectories);

  while (it.hasNext())
  {
    if (it.fileInfo().isDir() && it.fileName() != "." && it.fileName() != "..")
    {
      QString path = it.filePath();
      QDir dir(path);

      QFileInfoList infoList = dir.entryInfoList(QDir::AllEntries | QDir::System | QDir::NoDotAndDotDot | QDir::Hidden);
      if (infoList.isEmpty())
      {
        QDir().rmdir(path);
      }
    }

    it.next();
  }
}

void MusicSourceFolder::Sort(const QString& prefix)
{
  if (m_sFolder.compare(prefix) != 0)
    return;

  std::deque<CopyInfo> cis;
  GatherFilesToSort(m_sFolder, cis);

  if (!cis.empty())
  {
    SortLibraryDlg sortDlg(m_sFolder, cis, &MusicSourceFolder::ExecuteFileSort, nullptr);
    sortDlg.exec();
  }

  DeleteEmptyFolders(m_sFolder);

  if (cis.empty())
  {
    QMessageBox::information(nullptr, "Form1", "All files are already sorted.", QMessageBox::StandardButton::Ok, QMessageBox::StandardButton::Ok);
  }
}
