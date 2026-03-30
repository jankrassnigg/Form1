#pragma once

#include "Misc/Common.h"
#include <QDialog>
#include <ui_RadioPlaylistDlg.h>

class RadioPlaylist;

class RadioPlaylistDlg : public QDialog, Ui_RadioPlaylistDlg
{
  Q_OBJECT

public:
  RadioPlaylistDlg(RadioPlaylist* playlist, QWidget* parent);

private slots:
  void on_UsePlaylist1_toggled();
  void on_UsePlaylist2_toggled();
  void on_UsePlaylist3_toggled();
  void on_UsePlaylist4_toggled();
  void on_UsePlaylist5_toggled();
  void on_Buttons_clicked(QAbstractButton* pButton);

private:
  RadioPlaylist* m_Playlist = nullptr;
};
