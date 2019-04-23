#pragma once

#include "Misc/Common.h"
#include <QDateTime>
#include <QUuid>
#include <deque>

struct Modification
{
  // Template interface:
  //
  // void Apply(context) const;
  // void Save(QDataStream& stream) const;
  // void Load(QDataStream& stream);
  // void Coalesce(ModificationRecorder<Modification>& recorder, size_t passThroughIndex);

  QDateTime m_ModTimestamp;
  QString m_sModGuid;
};

template<typename T, typename CONTEXT>
class ModificationRecorder
{
public:
  mutable bool m_bRecordedModifcations = false;

  void AddModification(const T& mod, CONTEXT context)
  {
    m_bRecordedModifcations = true;

    m_Modifications.push_back(mod);
    m_Modifications.back().m_ModTimestamp = QDateTime::currentDateTimeUtc();
    m_Modifications.back().m_sModGuid = QUuid::createUuid().toString();

    mod.Apply(context);
  }

  void EnsureModificationExists(const T& mod, CONTEXT context)
  {
    for (size_t i = 0; i < m_Modifications.size(); ++i)
    {
      if (m_Modifications[i].HasModificationData(mod))
        return;
    }

    m_bRecordedModifcations = true;

    m_Modifications.push_back(mod);
    m_Modifications.back().m_ModTimestamp = QDateTime::currentDateTimeUtc();
    m_Modifications.back().m_sModGuid = QUuid::createUuid().toString();

    // do not apply the modification
  }

  void Save(QDataStream& stream) const
  {
    int version = 1;
    stream << version;

    int numMods = (int)m_Modifications.size();
    stream << numMods;

    for (int i = 0; i < numMods; ++i)
    {
      stream << m_Modifications[i].m_sModGuid;
      stream << m_Modifications[i].m_ModTimestamp;
      m_Modifications[i].Save(stream);
    }

    m_bRecordedModifcations = false;
  }

  void LoadAdditional(QDataStream& stream)
  {
    int version = 0;
    stream >> version;

    if (version != 1)
      return;

    int numMods = 0;
    stream >> numMods;

    for (int i = 0; i < numMods; ++i)
    {
      m_Modifications.push_back(T());
      T& mod = m_Modifications.back();

      stream >> mod.m_sModGuid;
      stream >> mod.m_ModTimestamp;
      mod.Load(stream);

      // TODO: not very efficient
      for (size_t j = 0; j < m_Modifications.size() - 1; ++j)
      {
        // remove the new modification, if one with the same GUID already exists
        if (m_Modifications[j].m_sModGuid == mod.m_sModGuid)
        {
          m_Modifications.pop_back();
          break;
        }
      }
    }
  }

  void ApplyAll(CONTEXT context)
  {
    sort(m_Modifications.begin(), m_Modifications.end(), [](const T& lhs, const T& rhs) -> bool
    {
      return lhs.m_ModTimestamp < rhs.m_ModTimestamp;
    });

    for (const auto& mod : m_Modifications)
    {
      mod.Apply(context);
    }
  }

  void CoalesceEntries()
  {
    if (m_Modifications.empty())
      return;

    sort(m_Modifications.begin(), m_Modifications.end(), [](const T& lhs, const T& rhs) -> bool
    {
      return lhs.m_ModTimestamp < rhs.m_ModTimestamp;
    });

    for (size_t i = m_Modifications.size(); i > 0; --i)
    {
      auto& mod = m_Modifications[i - 1];

      if (!mod.m_ModTimestamp.isValid())
        continue;

      mod.Coalesce(*this, i - 1);
    }

    sort(m_Modifications.begin(), m_Modifications.end(), [](const T& lhs, const T& rhs) -> bool
    {
      if (!lhs.m_ModTimestamp.isValid() && !rhs.m_ModTimestamp.isValid()) // equal
        return false;
      if (!lhs.m_ModTimestamp.isValid())
        return true;
      if (!rhs.m_ModTimestamp.isValid())
        return false;

      return lhs.m_ModTimestamp < rhs.m_ModTimestamp;
    });

    while (!m_Modifications.empty())
    {
      const auto& mod = m_Modifications.front();

      if (mod.m_ModTimestamp.isValid())
        break;

      m_Modifications.pop_front();
    }

    while (!m_Modifications.empty())
    {
      const auto& mod = m_Modifications.back();

      if (mod.m_ModTimestamp.isValid())
        break;

      m_Modifications.pop_back();
    }
  }

private:
  friend T;

  template<typename InvalidateIf>
  void InvalidatePrevious(size_t index, InvalidateIf ii)
  {
    for (size_t i = index; i > 0; --i)
    {
      auto& mod = m_Modifications[i - 1];

      if (!mod.m_ModTimestamp.isValid())
        continue;

      if (ii((const T&)mod))
      {
        mod.m_ModTimestamp = QDateTime();
      }
    }
  }

  void InvalidateThis(size_t index)
  {
    auto& mod = m_Modifications[index];
    mod.m_ModTimestamp = QDateTime();
  }

  std::deque<T> m_Modifications;
};
