#include "SoundDeviceBass.h"
#include <QTimer>

SoundDevice* SoundDevice::s_pSingleton = nullptr;


void SoundDeviceBass::Startup()
{
  if (!BASS_Init(-1, 44100, 0, nullptr, NULL))
  {
  }

  QTimer::singleShot(200, this, &SoundDeviceBass::onUpdatePosition);
}

void SoundDeviceBass::Shutdown()
{
  BASS_Free();
}

static void CALLBACK MediaFinishedCallback(HSYNC handle, DWORD channel, DWORD data, void *user)
{
  SoundDeviceBass* pDevice = reinterpret_cast<SoundDeviceBass*>(user);

  pDevice->TriggerFinished();
}

bool SoundDeviceBass::SetMedia(const char* szFile, int startOffsetMS, int endOffsetMS)
{
  StopPlaying();

  const QString tmp = QString::fromUtf8(szFile);

#ifdef Q_OS_WIN32
  m_MediaStream = BASS_StreamCreateFile(FALSE, tmp.toStdWString().data(), 0, 0, BASS_UNICODE);
#else
  m_MediaStream = BASS_StreamCreateFile(FALSE, tmp.toUtf8().data(), 0, 0, BASS_UNICODE);
#endif

  const int err = BASS_ErrorGetCode();
  if (m_MediaStream == 0 || err != BASS_OK)
  {
    StopPlaying();
    emit MediaError();
    return false;
  }

  BASS_ChannelSetAttribute(m_MediaStream, BASS_ATTRIB_VOL, m_fVolume);
  BASS_ChannelSetSync(m_MediaStream, BASS_SYNC_END, 0, MediaFinishedCallback, this);

  if (startOffsetMS > 0)
  {
    QWORD bytes = BASS_ChannelSeconds2Bytes(m_MediaStream, startOffsetMS / 1000.0);
    BASS_ChannelSetPosition(m_MediaStream, bytes, BASS_POS_BYTE);
  }

  if (endOffsetMS > 0)
  {
    QWORD bytes = BASS_ChannelSeconds2Bytes(m_MediaStream, endOffsetMS / 1000.0);
    BASS_ChannelSetSync(m_MediaStream, BASS_SYNC_POS, bytes, MediaFinishedCallback, this);
  }

  emit MediaReady();
  return true;
}

void SoundDeviceBass::StartPlaying()
{
  if (m_MediaStream)
  {
    BASS_ChannelPlay(m_MediaStream, FALSE);
  }
}

void SoundDeviceBass::StopPlaying()
{
  if (m_MediaStream)
  {
    BASS_ChannelStop(m_MediaStream);
    BASS_StreamFree(m_MediaStream);
    m_MediaStream = 0;
  }
}

void SoundDeviceBass::PausePlaying()
{
  if (m_MediaStream)
  {
    BASS_ChannelPause(m_MediaStream);
  }
}

void SoundDeviceBass::SetVolume(float volume)
{
  m_fVolume = volume;

  if (m_MediaStream)
  {
    BASS_ChannelSetAttribute(m_MediaStream, BASS_ATTRIB_VOL, m_fVolume);
  }
}

float SoundDeviceBass::GetVolume() const
{
  return m_fVolume;
}

double SoundDeviceBass::GetDuration() const
{
  if (m_MediaStream)
  {
    QWORD len = BASS_ChannelGetLength(m_MediaStream, BASS_POS_BYTE);
    return BASS_ChannelBytes2Seconds(m_MediaStream, len);
  }

  return 0;
}

double SoundDeviceBass::GetPosition() const
{
  if (m_MediaStream)
  {
    QWORD pos = BASS_ChannelGetPosition(m_MediaStream, BASS_POS_BYTE);
    return BASS_ChannelBytes2Seconds(m_MediaStream, pos);
  }

  return 0;
}

void SoundDeviceBass::SetNormalizedPosition(double norm)
{
  if (!m_MediaStream)
    return;

  double pos = GetDuration() * norm;
  QWORD bytes = BASS_ChannelSeconds2Bytes(m_MediaStream, pos);
  BASS_ChannelSetPosition(m_MediaStream, bytes, BASS_POS_BYTE);
}

void SoundDeviceBass::TriggerFinished()
{
  emit MediaFinished();
}

void SoundDeviceBass::onUpdatePosition()
{
  if (m_MediaStream)
  {
    emit MediaPositionChanged();
  }

  QTimer::singleShot(200, this, &SoundDeviceBass::onUpdatePosition);
}
