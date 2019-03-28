#pragma once

#include "Common.h"
#include <QObject>

class SoundDevice : public QObject
{

  Q_OBJECT

public:
  static SoundDevice* s_pSingleton;
  static SoundDevice* GetSingleton() { return s_pSingleton; }

  virtual void Startup() = 0;
  virtual void Shutdown() = 0;

  virtual bool SetMedia(const char* szFile, int startOffsetMS, int endOffsetMS) = 0;
  virtual void StartPlaying() = 0;
  virtual void StopPlaying() = 0;
  virtual void PausePlaying() = 0;
  virtual void SetVolume(float volume) = 0;
  virtual float GetVolume() const = 0;
  virtual double GetDuration() const = 0;
  virtual double GetPosition() const = 0;
  virtual void SetNormalizedPosition(double norm) = 0;

signals:
  void MediaReady();
  void MediaFinished();
  void MediaError();
  void MediaPositionChanged();
};

