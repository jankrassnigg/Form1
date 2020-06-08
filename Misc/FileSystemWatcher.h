#pragma once

#include <functional>
#include <string>
#include <QString>

struct ezDirectoryWatcherImpl;

/// \brief Which action has been performed on a file.
enum class ezDirectoryWatcherAction
{
  None,
  Added,
  Removed,
  Modified,
  RenamedOldName,
  RenamedNewName,
};

/// \brief
///   Watches file actions in a directory. Changes need to be polled.
class ezDirectoryWatcher
{
public:
  /// \brief What to watch out for.
  enum Watch
  {
    Reads = 1 << 0,         ///< Watch for reads.
    Writes = 1 << 1,        ///< Watch for writes.
    Creates = 1 << 2,       ///< Watch for newly created files.
    Renames = 1 << 3,       ///< Watch for renames.
    Subdirectories = 1 << 4 ///< Watch files in subdirectories recursively.
  };

  ezDirectoryWatcher();
  ~ezDirectoryWatcher();

  /// \brief
  ///   Opens the directory at \p absolutePath for watching. \p whatToWatch controls what exactly should be watched.
  ///
  /// \note A instance of ezDirectoryWatcher can only watch one directory at a time.
  bool OpenDirectory(const QString& absolutePath, uint32_t whatToWatch);

  /// \brief
  ///   Closes the currently watched directory if any.
  void CloseDirectory();

  /// \brief
  ///   Calls the callback \p func for each change since the last call. For each change the filename
  ///   and the action, which was performed on the file, is passed to \p func.
  ///
  /// \note There might be multiple changes on the same file reported.
  void EnumerateChanges(std::function<void(const QString& filename, ezDirectoryWatcherAction action)> func);

private:
  QString m_sDirectoryPath;
  ezDirectoryWatcherImpl* m_pImpl;
};
