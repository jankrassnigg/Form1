#pragma once

#include "Common.h"
#include <QObject.h>

class AppConfig : public QObject
{
  Q_OBJECT

public:
  AppConfig();
  ~AppConfig();

  static AppConfig* GetSingleton();

  void Load(const QString& sAppDir);
  void Save(const QString& sAppDir);

  const std::vector<QString>& GetAllMusicSources() const { return m_MusicSources; }

  void RemoveMusicSource(int index);
  bool AddMusicSource(const QString& path);

  void SetProfileDirectory(const QString& sDirectory);
  const QString& GetProfileDirectory() const { return m_sProfileDirectory; }

signals:
  void MusicSourceAdded(const QString& path);
  void MusicSourcesChanged();
  void ProfileDirectoryChanged();

private:
  QString m_sProfileDirectory;
  std::vector<QString> m_MusicSources;

  static AppConfig* s_pSingleton;
};
