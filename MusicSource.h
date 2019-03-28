#pragma once

#include "Common.h"
#include <QObject>

class MusicSource : public QObject
{
  Q_OBJECT

public:
  MusicSource();
  ~MusicSource();

  virtual void Startup() = 0;
  virtual void Shutdown() = 0;
  virtual void Sort(const QString& prefix) {}
};
