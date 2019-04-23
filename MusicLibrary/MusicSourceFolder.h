#pragma once

#include "MusicLibrary/MusicSource.h"
#include <QFuture>

class QFileInfo;

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
  void ParseFolder();
  QString ComputeFileGuid(const QString& sFilepath) const;
  bool UpdateFile(const QFileInfo& info);

  QString m_sFolder;
  QFuture<void> m_WorkerTask;
  volatile bool m_bShutdownWorker = false;
};
