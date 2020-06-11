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

private:
  RadioPlaylist* m_Playlist = nullptr;
};
