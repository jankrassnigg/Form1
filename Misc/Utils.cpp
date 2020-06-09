#include "Misc/Song.h"
#include <QFileInfo>
#include <QString>
#include <taglib/fileref.h>
#include <taglib/tag.h>
#include <taglib/tpropertymap.h>

int Clamp(int val, int minVal, int maxVal)
{
  if (val < minVal)
    return minVal;

  if (val > maxVal)
    return maxVal;

  return val;
}

QString ToTime(quint64 ms)
{
  const int minutes = ms / (60 * 1000);
  ms -= minutes * (60 * 1000);

  const int seconds = ms / 1000;
  ms -= seconds * 1000;

  return QString("%1:%2").arg(minutes).arg(seconds, 2, 10, QLatin1Char('0'));
}

int Min(int l, int r)
{
  return l < r ? l : r;
}

int Max(int l, int r)
{
  return l > r ? l : r;
}

void SongInfo::Clear()
{
  m_sTitle.clear();
  m_sArtist.clear();
  m_sAlbum.clear();
  m_iTrackNumber = 0;
  m_iYear = 0;
  m_iLengthInMS = 0;
}

bool SongInfo::ReadSongInfo(const QString& sFile)
{
  Clear();

#ifdef Q_OS_WIN32
  TagLib::FileRef file(sFile.toStdWString().data());
#else
  TagLib::FileRef file(sFile.toUtf8().data());
#endif

  const TagLib::Tag* tag = file.tag();

  m_sTitle = QFileInfo(sFile).baseName();

  m_iLengthInMS = file.audioProperties()->lengthInMilliseconds();

  if (tag == nullptr)
    return false;

  if (m_sTitle[1] == '-')
  {
    QString c = m_sTitle[0];
    m_iDiscNumber = c.toInt();

    m_sTitle.remove(0, 2);
  }

  if (!tag->title().isNull() && !tag->title().isEmpty())
    m_sTitle = tag->title().toCString(true);

  if (!tag->artist().isNull() && !tag->artist().isEmpty())
    m_sArtist = tag->artist().toCString(true);

  if (!tag->album().isNull() && !tag->album().isEmpty())
    m_sAlbum = tag->album().toCString(true);

  if (tag->track() != 0)
    m_iTrackNumber = tag->track();

  if (tag->year() != 0)
    m_iYear = tag->year();

  // unfortunately this does not seem to be the way to find additional information
  //if (!tag->properties().isEmpty())
  //{
  //  const auto& propmap = tag->properties();

  //  for (auto propIt = propmap.begin(); propIt != propmap.end(); ++propIt)
  //  {
  //    const auto& name = propIt->first;

  //    if (name == "ARTIST")
  //      continue;
  //    if (name == "ALBUM")
  //      continue;
  //    if (name == "COMMENT")
  //      continue;
  //    if (name == "GENRE")
  //      continue;
  //    if (name == "TITLE")
  //      continue;
  //    if (name == "DATE")
  //      continue;
  //    if (name == "TRACKNUMBER")
  //      continue;

  //    if (name == "DISCNUMBER")
  //    {
  //      const auto& sl = propIt->second;

  //      for (auto e = sl.begin(); e != sl.end(); ++e)
  //      {
  //        const QString val = QString::fromWCharArray(e->toCWString());

  //        bool ok = false;
  //        int disc = val.toInt(&ok);
  //        if (ok)
  //        {
  //          m_iDiscNumber = (signed char)disc;
  //          break;
  //        }
  //      }
  //    }
  //  }
  //}

  return true;
}

bool SongInfo::ModifyFileTag(const QString& sFile, const SongInfo& info, unsigned int PartMask)
{
  if (PartMask == 0)
    return true;

#ifdef Q_OS_WIN32
  TagLib::FileRef file(sFile.toStdWString().data());
#else
  TagLib::FileRef file(sFile.toUtf8().data());
#endif

  TagLib::Tag* tag = file.tag();

  if (tag == nullptr)
    return false;

  if ((PartMask & SongInfo::Part::Title) != 0)
    tag->setTitle(TagLib::String(info.m_sTitle.toUtf8().data(), TagLib::String::UTF8));

  if ((PartMask & SongInfo::Part::Artist) != 0)
    tag->setArtist(TagLib::String(info.m_sArtist.toUtf8().data(), TagLib::String::UTF8));

  if ((PartMask & SongInfo::Part::Album) != 0)
    tag->setAlbum(TagLib::String(info.m_sAlbum.toUtf8().data(), TagLib::String::UTF8));

  if ((PartMask & SongInfo::Part::Track) != 0)
    tag->setTrack(info.m_iTrackNumber);

  if ((PartMask & SongInfo::Part::Year) != 0)
    tag->setYear(info.m_iYear);

  return file.save();
}
