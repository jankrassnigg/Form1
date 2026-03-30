#pragma once

#include "SoundDevice.h"
#include <QMediaPlayer>

class SoundDeviceQt : public SoundDevice
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

protected slots:
  void onPlaybackPositionChanged(qint64 pos);
  void onPlaybackStateChanged(QMediaPlayer::MediaStatus status);

private:
  QScopedPointer<QMediaPlayer> m_pMediaPlayer;
};
