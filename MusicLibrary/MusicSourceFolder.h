#pragma once

#include "MusicLibrary/MusicSource.h"

#include <QFuture>
#include <deque>

class QFileInfo;

struct CopyInfo
{
  QString m_sGuid;
  QString m_sSource;
  QString m_sTargetFolder;
  QString m_sTargetFile;
};

class MusicSourceFolder : public MusicSource
{
  Q_OBJECT

public:
  MusicSourceFolder(const QString& path);
  ~MusicSourceFolder();

  virtual void Startup() override;
  virtual void Shutdown() override;
  virtual void Sort(const QString& prefix) override;

private:
  static void GatherFilesToSort(const QString& prefix, std::deque<CopyInfo>& cis);
  static bool ExecuteFileSort(const CopyInfo& ci, QString& outError);
  static void DeleteEmptyFolders(const QString& folder);

  void ParseFolder();
  QString ComputeFileGuid(const QString& sFilepath) const;
  bool UpdateFile(const QFileInfo& info);

  QString m_sFolder;
  QFuture<void> m_WorkerTask;
  volatile bool m_bShutdownWorker = false;
};
