#pragma once

#include "SoundDevice.h"
#include "bass.h"

class SoundDeviceBass : public SoundDevice
{
  Q_OBJECT

public:
  virtual void Startup() override;
  virtual void Shutdown() override;
  virtual bool SetMedia(const char* szFile, int startOffsetMS, int endOffsetMS) override;
  virtual void StartPlaying() override;
  virtual void StopPlaying() override;
  virtual void PausePlaying() override;
  virtual void SetVolume(float volume) override;
  virtual float GetVolume() const override;
  virtual double GetDuration() const override;
  virtual double GetPosition() const override;
  virtual void SetNormalizedPosition(double norm) override;

  void TriggerFinished();

protected slots:
  void onUpdatePosition();

private:
  double m_fVolume = 1.0;
  HSTREAM m_MediaStream;
};
