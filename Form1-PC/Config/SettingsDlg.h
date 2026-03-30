#pragma once

#include "Misc/Common.h"
#include <QDialog>
#include <ui_SettingsDlg.h>

class SettingsDlg : public QDialog, Ui_SettingsDlg
{
  Q_OBJECT

public:
  SettingsDlg();
  ~SettingsDlg();

private slots:
  void on_BrowseProfileDirButton_clicked();
  void on_RemoveMusicDirButton_clicked();
  void on_AddMusicDirButton_clicked();
  void on_MusicDirsList_itemSelectionChanged();
  void onSortDirClicked();

private:
  void FillMusicFoldersList();
};
