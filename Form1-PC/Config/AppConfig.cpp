#include "Config/AppConfig.h"
#include <QDataStream>
#include <QFile>

AppConfig* AppConfig::s_pSingleton = nullptr;

AppConfig::AppConfig()
{
  s_pSingleton = this;
}

AppConfig::~AppConfig()
{
  s_pSingleton = nullptr;
}

AppConfig* AppConfig::GetSingleton()
{
  return s_pSingleton;
}

void AppConfig::Load(const QString& sAppDir)
{
  m_MusicSources.clear();

  m_sProfileDirectory = sAppDir + "/Profile";
  const QString sFile = sAppDir + "/Form1.cfg";

  QFile file(sFile);
  if (!file.open(QIODevice::OpenModeFlag::ReadOnly))
    return;

  QDataStream stream(&file);

  int version = 0;
  stream >> version;

  if (version != 1 && version != 2)
    return;

  stream >> m_sProfileDirectory;

  int numSources = 0;
  stream >> numSources;

  m_MusicSources.resize(numSources);

  for (int i = 0; i < numSources; ++i)
  {
    stream >> m_MusicSources[i];
  }

  if (version >= 2)
  {
    stream >> m_bShowRateSongPopup;
  }
}

void AppConfig::Save(const QString& sAppDir)
{
  const QString sFile = sAppDir + "/Form1.cfg";

  QFile file(sFile);
  if (!file.open(QIODevice::OpenModeFlag::WriteOnly))
    return;

  QDataStream stream(&file);

  int version = 2;
  stream << version;

  stream << m_sProfileDirectory;

  const int numSources = (int)m_MusicSources.size();
  stream << numSources;

  for (int i = 0; i < numSources; ++i)
  {
    stream << m_MusicSources[i];
  }

  stream << m_bShowRateSongPopup;
}

void AppConfig::RemoveMusicSource(int index)
{
  m_MusicSources.erase(m_MusicSources.begin() + index);
  emit MusicSourcesChanged();
}

bool AppConfig::AddMusicSource(const QString& path)
{
  if (find(m_MusicSources.begin(), m_MusicSources.end(), path) != m_MusicSources.end())
    return false;

  m_MusicSources.push_back(path);
  sort(m_MusicSources.begin(), m_MusicSources.end());

  emit MusicSourceAdded(path);
  emit MusicSourcesChanged();

  return true;
}

void AppConfig::SetProfileDirectory(const QString& sDirectory)
{
  if (m_sProfileDirectory == sDirectory)
    return;

  m_sProfileDirectory = sDirectory;

  emit ProfileDirectoryChanged();
}
