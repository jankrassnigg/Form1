#pragma once

#include "Common.h"

#include <list>

class SmartPlaylistQuery
{
public:
  enum class Fulfil
  {
    All,
    Any,
  };

  enum class Criterium
  {
    Artist,
    Album,
    Title,
    Year,
    TrackNumber,
    Length,
    Rating,
    LastPlayed,
    DateAdded,
    PlayCount,
    DiscNumber,

    ENUM_COUNT
  };

  enum class Comparison
  {
    Equal,
    NotEqual,
    Less,
    LessEqual,
    Greater,
    GreaterEqual,

    Is,
    IsNot,
    Contains,
    ContainsNot,
    StartsWith,
    EndsWith,

    IsUnknown,
  };

  enum class SortOrder
  {
    Random,
    ArtistAtoZ,
    AlbumAtoZ,
    TitleAtoZ,
    YearNewToOld,
    YearOldToNew,
    DurationShortToLong,
    DurationLongToShort,
    PlayDateOld,
    PlayDataNew,
    PlayCountLow,
    PlayCountHigh,
    RatingLow,
    RatingHigh,
    DateAddedOld,
    DateAddedNew,

    ENUM_COUNT
  };

  struct Statement
  {
    Criterium m_Criterium = Criterium::Artist;
    Comparison m_Compare = Comparison::Contains;
    QString m_Value;

    QString GenerateSQL() const;
  };

  struct ConditionGroup
  {
    Fulfil m_Fulfil = Fulfil::All;

    std::list<Statement> m_Statements;
    std::list<ConditionGroup> m_SubGroups;

    QString GenerateSQL() const;
  };

  static void GetAllowedComparisons(Criterium crit, std::vector<Comparison>& out_Comparisons);
  static QString ToUiString(Criterium c);
  static QString ToUiString(Comparison c);
  static QString ToUiString(SortOrder so);
  static bool AdjustComparison(Criterium crit, Comparison& inout_Compare);

  static QString ToDbString(Criterium c);
  static QString ToDbString(Comparison c, const QString& value);

  QString GenerateSQL() const;
  QString GenerateOrderBySQL() const;

  void Save(QDataStream& stream) const;
  void Load(QDataStream& stream);

  SortOrder m_SortOrder = SortOrder::Random;
  int m_iSongLimit = 0;
  ConditionGroup m_MainGroup;

private:
  void Save(QDataStream& stream, const ConditionGroup& group) const;
  void Load(QDataStream& stream, ConditionGroup& group);

};

