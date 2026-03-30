#pragma once

#include "Misc/Common.h"
#include "Misc/Song.h"
#include <QDialog>
#include <set>
#include <ui_SongInfoDlg.h>

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
  void on_StartOffsetNow_clicked();
  void on_EndOffsetNow_clicked();

private:
  void ConvertTime(int curTimeMS, int& out_Minutes, int& out_Seconds, int& out_Milliseconds);

  std::set<QString>& m_SelectedSongs;
  SongInfo m_SharedInfos;
  std::map<QString, QString> m_AllLocations; // key is the file path, value is the song guid that it belongs to

protected:
  virtual bool eventFilter(QObject*, QEvent*) override;

  QString m_sCheckedArtist;
};
