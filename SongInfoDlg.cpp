#include "AppConfig.h"
#include "SongInfoDlg.h"
#include "MusicLibrary.h"
#include <QDialogButtonBox>
#include <QPushButton>

SongInfoDlg::SongInfoDlg(std::set<QString>& selectedSongs, QWidget* parent)
  : QDialog(parent)
  , m_SelectedSongs(selectedSongs)
{
  setupUi(this);

  RatingCombo->addItem("Not rated");
  RatingCombo->addItem("1 Star");
  RatingCombo->addItem("2 Stars");
  RatingCombo->addItem("3 Stars");
  RatingCombo->addItem("4 Stars");
  RatingCombo->addItem("5 Stars");

  MusicLibrary::GetSingleton()->FindSong(*m_SelectedSongs.begin(), m_SharedInfos);

  std::deque<QString> locations;

  for (const QString& guid : m_SelectedSongs)
  {
    SongInfo info;
    MusicLibrary::GetSingleton()->FindSong(guid, info);
    MusicLibrary::GetSingleton()->GetSongLocations(guid, locations);

    for (const QString& loc : locations)
    {
      m_AllLocations.insert(loc);
    }

    if (info.m_sTitle != m_SharedInfos.m_sTitle)
      m_SharedInfos.m_sTitle = QString();

    if (info.m_sArtist != m_SharedInfos.m_sArtist)
      m_SharedInfos.m_sArtist = QString();

    if (info.m_sAlbum != m_SharedInfos.m_sAlbum)
      m_SharedInfos.m_sAlbum = QString();

    if (info.m_iTrackNumber != m_SharedInfos.m_iTrackNumber)
      m_SharedInfos.m_iTrackNumber = 0;

    if (info.m_iDiscNumber != m_SharedInfos.m_iDiscNumber)
      m_SharedInfos.m_iDiscNumber = 0;

    if (info.m_iYear != m_SharedInfos.m_iYear)
      m_SharedInfos.m_iYear = 0;

    if (info.m_iLengthInMS != m_SharedInfos.m_iLengthInMS)
      m_SharedInfos.m_iLengthInMS = 0;

    if (info.m_iRating != m_SharedInfos.m_iRating)
      m_SharedInfos.m_iRating = 0;

    if (info.m_iVolume != m_SharedInfos.m_iVolume)
      m_SharedInfos.m_iVolume = 0;

    if (info.m_iStartOffset != m_SharedInfos.m_iStartOffset)
      m_SharedInfos.m_iStartOffset = 0;

    if (info.m_iEndOffset != m_SharedInfos.m_iEndOffset)
      m_SharedInfos.m_iEndOffset = 0;
  }

  TitleLineEdit->setText(m_SharedInfos.m_sTitle);
  ArtistLineEdit->setText(m_SharedInfos.m_sArtist);
  AlbumLineEdit->setText(m_SharedInfos.m_sAlbum);
  TrackLineEdit->setText(QString("%1").arg(m_SharedInfos.m_iTrackNumber));
  DiscLineEdit->setText(QString("%1").arg(m_SharedInfos.m_iDiscNumber));
  YearLineEdit->setText(QString("%1").arg(m_SharedInfos.m_iYear));
  LengthLineEdit->setText(ToTime(m_SharedInfos.m_iLengthInMS));
  VolumeSlider->setValue(m_SharedInfos.m_iVolume);
  StartOffsetLineEdit->setText(QString("%1").arg(m_SharedInfos.m_iStartOffset));
  EndOffsetLineEdit->setText(QString("%1").arg(m_SharedInfos.m_iEndOffset));
  RatingCombo->setCurrentIndex(m_SharedInfos.m_iRating);

  for (const QString& loc : m_AllLocations)
  {
    LocationsList->addItem(loc);
  }
}

void SongInfoDlg::on_ButtonBox_clicked(QAbstractButton* button)
{
  if (button == ButtonBox->button(QDialogButtonBox::Cancel))
  {
    reject();
    return;
  }

  if (button == ButtonBox->button(QDialogButtonBox::Save))
  {
    unsigned int partMask = 0;
    SongInfo si;

    if (EditTitle->isChecked())
    {
      partMask |= SongInfo::Part::Title;
      si.m_sTitle = TitleLineEdit->text();
    }

    if (EditArtist->isChecked())
    {
      partMask |= SongInfo::Part::Artist;
      si.m_sArtist = ArtistLineEdit->text();
    }

    if (EditAlbum->isChecked())
    {
      partMask |= SongInfo::Part::Album;
      si.m_sAlbum = AlbumLineEdit->text();
    }

    if (EditTrack->isChecked())
    {
      partMask |= SongInfo::Part::Track;
      si.m_iTrackNumber = TrackLineEdit->text().toInt();
    }

    if (EditDisc->isChecked())
    {
      partMask |= SongInfo::Part::DiscNumber;
      si.m_iDiscNumber = DiscLineEdit->text().toInt();
    }

    if (EditYear->isChecked())
    {
      partMask |= SongInfo::Part::Year;
      si.m_iYear = YearLineEdit->text().toInt();
    }

    if (EditRating->isChecked())
    {
      partMask |= SongInfo::Part::Rating;
      si.m_iRating = RatingCombo->currentIndex();
    }

    if (EditVolume->isChecked())
    {
      partMask |= SongInfo::Part::Volume;
      si.m_iVolume = VolumeSlider->value();
    }

    if (EditStartOffset->isChecked())
    {
      partMask |= SongInfo::Part::StartOffset;
      si.m_iStartOffset = StartOffsetLineEdit->text().toInt();
    }

    if (EditEndOffset->isChecked())
    {
      partMask |= SongInfo::Part::EndOffset;
      si.m_iEndOffset = EndOffsetLineEdit->text().toInt();
    }

    if ((partMask & (SongInfo::Part::Album | SongInfo::Part::Artist | SongInfo::Part::Title | SongInfo::Part::Track | SongInfo::Part::Year)) != 0)
    {
      for (const QString& loc : m_AllLocations)
      {
        if (!SongInfo::ModifyFileTag(loc, si, partMask))
        {
          printf("Failed to modify file '%s'\n", loc.toUtf8().data());
        }
      }
    }

    for (const QString& guid : m_SelectedSongs)
    {
      if ((partMask & SongInfo::Part::Title) != 0)
        MusicLibrary::GetSingleton()->UpdateSongTitle(guid, si.m_sTitle);

      if ((partMask & SongInfo::Part::Artist) != 0)
        MusicLibrary::GetSingleton()->UpdateSongArtist(guid, si.m_sArtist);

      if ((partMask & SongInfo::Part::Album) != 0)
        MusicLibrary::GetSingleton()->UpdateSongAlbum(guid, si.m_sAlbum);

      if ((partMask & SongInfo::Part::Track) != 0)
        MusicLibrary::GetSingleton()->UpdateSongTrackNumber(guid, si.m_iTrackNumber);

      if ((partMask & SongInfo::Part::DiscNumber) != 0)
        MusicLibrary::GetSingleton()->UpdateSongDiscNumber(guid, si.m_iDiscNumber, true);

      if ((partMask & SongInfo::Part::Year) != 0)
        MusicLibrary::GetSingleton()->UpdateSongYear(guid, si.m_iYear);

      if ((partMask & SongInfo::Part::Rating) != 0)
        MusicLibrary::GetSingleton()->UpdateSongRating(guid, si.m_iRating, true);

      if ((partMask & SongInfo::Part::Volume) != 0)
        MusicLibrary::GetSingleton()->UpdateSongVolume(guid, si.m_iVolume, true);

      if ((partMask & SongInfo::Part::StartOffset) != 0)
        MusicLibrary::GetSingleton()->UpdateSongStartOffset(guid, si.m_iStartOffset, true);

      if ((partMask & SongInfo::Part::EndOffset) != 0)
        MusicLibrary::GetSingleton()->UpdateSongEndOffset(guid, si.m_iEndOffset, true);
    }

    accept();
    return;
  }
}

void SongInfoDlg::on_EditTitle_toggled(bool checked)
{
  TitleLineEdit->setEnabled(checked);

  if (!checked)
    TitleLineEdit->setText(m_SharedInfos.m_sTitle);
}  

void SongInfoDlg::on_EditArtist_toggled(bool checked)
{
  ArtistLineEdit->setEnabled(checked);

  if (!checked)
    ArtistLineEdit->setText(m_SharedInfos.m_sArtist);
}

void SongInfoDlg::on_EditAlbum_toggled(bool checked)
{
  AlbumLineEdit->setEnabled(checked);

  if (!checked)
    AlbumLineEdit->setText(m_SharedInfos.m_sAlbum);
}

void SongInfoDlg::on_EditTrack_toggled(bool checked)
{
  TrackLineEdit->setEnabled(checked);

  if (!checked)
    TrackLineEdit->setText(QString("%1").arg(m_SharedInfos.m_iTrackNumber));
}

void SongInfoDlg::on_EditDisc_toggled(bool checked)
{
  DiscLineEdit->setEnabled(checked);

  if (!checked)
    DiscLineEdit->setText(QString("%1").arg(m_SharedInfos.m_iDiscNumber));
}

void SongInfoDlg::on_EditYear_toggled(bool checked)
{
  YearLineEdit->setEnabled(checked);

  if (!checked)
    YearLineEdit->setText(QString("%1").arg(m_SharedInfos.m_iYear));
}

void SongInfoDlg::on_EditVolume_toggled(bool checked)
{
  VolumeSlider->setEnabled(checked);

  if (!checked)
    VolumeSlider->setValue(m_SharedInfos.m_iVolume);
}

void SongInfoDlg::on_EditStartOffset_toggled(bool checked)
{
  StartOffsetLineEdit->setEnabled(checked);

  if (!checked)
    StartOffsetLineEdit->setText(QString("%1").arg(m_SharedInfos.m_iStartOffset));
}

void SongInfoDlg::on_EditEndOffset_toggled(bool checked)
{
  EndOffsetLineEdit->setEnabled(checked);

  if (!checked)
    EndOffsetLineEdit->setText(QString("%1").arg(m_SharedInfos.m_iEndOffset));
}

void SongInfoDlg::on_EditRating_toggled(bool checked)
{
  RatingCombo->setEnabled(checked);

  if (!checked)
    RatingCombo->setCurrentIndex(m_SharedInfos.m_iRating);

}

