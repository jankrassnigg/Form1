#pragma once

#include "Misc/Common.h"
#include "Misc/Song.h"
#include <QDialog>
#include <ui_SongInfoDlg.h>
#include <set>

class SongInfoDlg : public QDialog, Ui_SongInfoDlg
{
  Q_OBJECT

public:
  SongInfoDlg(std::set<QString>& selectedSongs, QWidget* parent);

private slots:
  void on_ButtonBox_clicked(QAbstractButton* button);
  void on_EditTitle_toggled(bool checked);
  void on_EditArtist_toggled(bool checked);
  void on_EditAlbum_toggled(bool checked);
  void on_EditTrack_toggled(bool checked);
  void on_EditDisc_toggled(bool checked);
  void on_EditYear_toggled(bool checked);
  void on_EditVolume_toggled(bool checked);
  void on_EditStartOffset_toggled(bool checked);
  void on_EditEndOffset_toggled(bool checked);
  void on_EditRating_toggled(bool checked);

private:
  std::set<QString>& m_SelectedSongs;
  SongInfo m_SharedInfos;
  std::set<QString> m_AllLocations;
};
