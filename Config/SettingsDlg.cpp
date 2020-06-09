#include "Config/SettingsDlg.h"
#include "Config/AppConfig.h"
#include "Config/AppState.h"
#include "MusicLibrary/MusicSource.h"
#include <QFileDialog>
#include <QTableWidget>

SettingsDlg::SettingsDlg()
{
  setupUi(this);

  MusicDirsList->setSelectionBehavior(QAbstractItemView::SelectionBehavior::SelectRows);
  MusicDirsList->setSelectionMode(QAbstractItemView::SelectionMode::SingleSelection);

  RemoveMusicDirButton->setEnabled(false);

  ProfileDirLineEdit->setText(AppConfig::GetSingleton()->GetProfileDirectory());

  ShowRatingDialog->setChecked(AppConfig::GetSingleton()->GetShowRateSongPopup());

  FillMusicFoldersList();
}

SettingsDlg::~SettingsDlg()
{
  AppConfig::GetSingleton()->SetShowRateSongPopup(ShowRatingDialog->isChecked());
}

void SettingsDlg::on_BrowseProfileDirButton_clicked()
{
  QString folder = QFileDialog::getExistingDirectory(this, "Select Profile Directory", ProfileDirLineEdit->text());

  if (folder.isEmpty())
    return;

  AppConfig::GetSingleton()->SetProfileDirectory(folder);

  ProfileDirLineEdit->setText(AppConfig::GetSingleton()->GetProfileDirectory());
}

void SettingsDlg::on_RemoveMusicDirButton_clicked()
{
  if (MusicDirsList->selectedItems().isEmpty())
    return;

  const int row = MusicDirsList->selectionModel()->selectedRows()[0].row();

  delete MusicDirsList->selectedItems()[0];

  MusicDirsList->clearSelection();

  AppConfig::GetSingleton()->RemoveMusicSource(row);

  FillMusicFoldersList();
}

void SettingsDlg::on_AddMusicDirButton_clicked()
{
  QString folder = QFileDialog::getExistingDirectory(this, "Select Music Folder", QString());

  if (folder.isEmpty())
    return;

  if (AppConfig::GetSingleton()->AddMusicSource(folder))
  {
    FillMusicFoldersList();
  }
}

void SettingsDlg::on_MusicDirsList_itemSelectionChanged()
{
  RemoveMusicDirButton->setEnabled(!MusicDirsList->selectedItems().isEmpty());
}

void SettingsDlg::FillMusicFoldersList()
{
  const auto& sources = AppConfig::GetSingleton()->GetAllMusicSources();

  MusicDirsList->clear();
  MusicDirsList->setColumnCount(2);
  MusicDirsList->setRowCount((int)sources.size());
  MusicDirsList->horizontalHeader()->hide();
  MusicDirsList->verticalHeader()->hide();
  MusicDirsList->horizontalHeader()->setSectionResizeMode(0, QHeaderView::ResizeMode::Stretch);
  MusicDirsList->horizontalHeader()->setSectionResizeMode(1, QHeaderView::ResizeMode::ResizeToContents);

  for (int row = 0; row < (int)sources.size(); ++row)
  {
    const QString& source = sources[row];

    QPushButton* pButton = new QPushButton("Sort...", MusicDirsList);
    connect(pButton, &QPushButton::clicked, this, &SettingsDlg::onSortDirClicked);

    pButton->setProperty("dir", source);

    MusicDirsList->setItem(row, 0, new QTableWidgetItem(source));
    MusicDirsList->setCellWidget(row, 1, pButton);
  }
}

void SettingsDlg::onSortDirClicked()
{
  QString sDir = sender()->property("dir").toString();

  const auto& sources = AppConfig::GetSingleton()->GetAllMusicSources();

  for (const unique_ptr<MusicSource>& ptr : AppState::GetSingleton()->GetAllMusicSources())
  {
    ptr->Sort(sDir);
  }
}
