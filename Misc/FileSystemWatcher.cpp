#include "FileSystemWatcher.h"

#include <windows.h>
#include <vector>

struct ezDirectoryWatcherImpl
{
  void DoRead();

  HANDLE m_directoryHandle;
  HANDLE m_completionPort;
  bool m_watchSubdirs;
  DWORD m_filter;
  OVERLAPPED m_overlapped;
  std::vector<uint8_t> m_buffer;
};

ezDirectoryWatcher::ezDirectoryWatcher()
    : m_pImpl(new ezDirectoryWatcherImpl)
{
  m_pImpl->m_buffer.resize(1024 * 1024);
}

bool ezDirectoryWatcher::OpenDirectory(const QString& absolutePath, uint32_t whatToWatch)
{
  QString sPath(absolutePath);

  m_pImpl->m_watchSubdirs = (whatToWatch & Watch::Subdirectories) != 0;
  m_pImpl->m_filter = 0;
  if ((whatToWatch & Watch::Reads) != 0)
    m_pImpl->m_filter |= FILE_NOTIFY_CHANGE_LAST_ACCESS;
  if ((whatToWatch & Watch::Writes) != 0)
    m_pImpl->m_filter |= FILE_NOTIFY_CHANGE_LAST_WRITE;
  if ((whatToWatch & Watch::Creates) != 0)
    m_pImpl->m_filter |= FILE_NOTIFY_CHANGE_CREATION;
  if ((whatToWatch & Watch::Renames) != 0)
    m_pImpl->m_filter |= FILE_NOTIFY_CHANGE_FILE_NAME | FILE_NOTIFY_CHANGE_DIR_NAME;

  m_pImpl->m_directoryHandle = CreateFileW(
    sPath.toStdWString().c_str(),
      FILE_LIST_DIRECTORY,
      FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
      nullptr,
      OPEN_EXISTING,
      FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OVERLAPPED,
      nullptr);
  if (m_pImpl->m_directoryHandle == INVALID_HANDLE_VALUE)
  {
    return false;
  }

  m_pImpl->m_completionPort = CreateIoCompletionPort(m_pImpl->m_directoryHandle, nullptr, 0, 1);
  if (m_pImpl->m_completionPort == INVALID_HANDLE_VALUE)
  {
    return false;
  }

  m_pImpl->DoRead();
  m_sDirectoryPath = sPath;

  return true;
}

void ezDirectoryWatcher::CloseDirectory()
{
  if (!m_sDirectoryPath.isEmpty())
  {
    CancelIo(m_pImpl->m_directoryHandle);
    CloseHandle(m_pImpl->m_completionPort);
    CloseHandle(m_pImpl->m_directoryHandle);
    m_sDirectoryPath.clear();
  }
}

ezDirectoryWatcher::~ezDirectoryWatcher()
{
  CloseDirectory();
  delete m_pImpl;
}

void ezDirectoryWatcherImpl::DoRead()
{
  memset(&m_overlapped, 0, sizeof(m_overlapped));
  BOOL success = ReadDirectoryChangesW(m_directoryHandle, m_buffer.data(), (DWORD)m_buffer.size(), m_watchSubdirs,
                                       m_filter, nullptr, &m_overlapped, nullptr);
}

void ezDirectoryWatcher::EnumerateChanges(std::function<void(const QString& filename, ezDirectoryWatcherAction action)> func)
{
  OVERLAPPED* lpOverlapped;
  DWORD numberOfBytes;
  ULONG_PTR completionKey;

  while (GetQueuedCompletionStatus(m_pImpl->m_completionPort, &numberOfBytes, &completionKey, &lpOverlapped, 0) != 0)
  {
    if (numberOfBytes <= 0)
    {
      m_pImpl->DoRead();
      continue;
    }
    //Copy the buffer

    std::vector<uint8_t> buffer;
    buffer.resize(numberOfBytes);
    memcpy(buffer.data(), m_pImpl->m_buffer.data(), numberOfBytes);

    //Reissue the read request
    m_pImpl->DoRead();

    //Progress the messages
    auto info = (const FILE_NOTIFY_INFORMATION*)buffer.data();
    while (true)
    {
      QString filename = QString::fromWCharArray(info->FileName, info->FileNameLength / sizeof(WCHAR));

      if (!filename.isEmpty())
      {
        ezDirectoryWatcherAction action = ezDirectoryWatcherAction::None;
        switch (info->Action)
        {
        case FILE_ACTION_ADDED:
          action = ezDirectoryWatcherAction::Added;
          break;
        case FILE_ACTION_REMOVED:
          action = ezDirectoryWatcherAction::Removed;
          break;
        case FILE_ACTION_MODIFIED:
          action = ezDirectoryWatcherAction::Modified;
          break;
        case FILE_ACTION_RENAMED_OLD_NAME:
          action = ezDirectoryWatcherAction::RenamedOldName;
          break;
        case FILE_ACTION_RENAMED_NEW_NAME:
          action = ezDirectoryWatcherAction::RenamedNewName;
          break;
        }

        func(filename, action);
      }

      if (info->NextEntryOffset == 0)
        break;
      else
        info = (const FILE_NOTIFY_INFORMATION*)(((uint8_t*)info) + info->NextEntryOffset);
    }
  }
}
