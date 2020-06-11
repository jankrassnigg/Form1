#include "Playlists/Smart/SmartPlaylistQuery.h"
#include "MusicLibrary/MusicLibrary.h"
#include <assert.h>

void SmartPlaylistQuery::GetAllowedComparisons(Criterium crit, std::vector<Comparison>& out_Comparisons)
{
  out_Comparisons.clear();

  switch (crit)
  {
  case Criterium::Artist:
  case Criterium::Album:
  case Criterium::Title:
    out_Comparisons.push_back(SmartPlaylistQuery::Comparison::Contains);
    out_Comparisons.push_back(SmartPlaylistQuery::Comparison::ContainsNot);
    out_Comparisons.push_back(SmartPlaylistQuery::Comparison::StartsWith);
    out_Comparisons.push_back(SmartPlaylistQuery::Comparison::EndsWith);
    out_Comparisons.push_back(SmartPlaylistQuery::Comparison::Is);
    out_Comparisons.push_back(SmartPlaylistQuery::Comparison::IsNot);
    out_Comparisons.push_back(SmartPlaylistQuery::Comparison::IsUnknown);
    break;

  case Criterium::Year:
  case Criterium::TrackNumber:
  case Criterium::Length:
  case Criterium::Rating:
  case Criterium::PlayCount:
  case Criterium::DiscNumber:
    out_Comparisons.push_back(SmartPlaylistQuery::Comparison::Equal);
    out_Comparisons.push_back(SmartPlaylistQuery::Comparison::NotEqual);
    out_Comparisons.push_back(SmartPlaylistQuery::Comparison::Less);
    out_Comparisons.push_back(SmartPlaylistQuery::Comparison::LessEqual);
    out_Comparisons.push_back(SmartPlaylistQuery::Comparison::Greater);
    out_Comparisons.push_back(SmartPlaylistQuery::Comparison::GreaterEqual);
    break;

  case Criterium::LastPlayed:
  case Criterium::DateAdded:
    out_Comparisons.push_back(SmartPlaylistQuery::Comparison::Equal);
    out_Comparisons.push_back(SmartPlaylistQuery::Comparison::NotEqual);
    out_Comparisons.push_back(SmartPlaylistQuery::Comparison::Less);
    out_Comparisons.push_back(SmartPlaylistQuery::Comparison::LessEqual);
    out_Comparisons.push_back(SmartPlaylistQuery::Comparison::Greater);
    out_Comparisons.push_back(SmartPlaylistQuery::Comparison::GreaterEqual);
    out_Comparisons.push_back(SmartPlaylistQuery::Comparison::IsUnknown);
    break;

  default:
    assert(false && "Missing case statement");
  }
}

QString SmartPlaylistQuery::ToUiString(Criterium c)
{
  switch (c)
  {
  case Criterium::Artist:
    return "Artist";

  case Criterium::Album:
    return "Album";

  case Criterium::Title:
    return "Title";

  case Criterium::Year:
    return "Year";

  case Criterium::TrackNumber:
    return "Track Number";

  case Criterium::Length:
    return "Duration";

  case Criterium::Rating:
    return "Rating";

  case Criterium::LastPlayed:
    return "Last Played";

  case Criterium::DateAdded:
    return "Date Added";

  case Criterium::PlayCount:
    return "Play Count";

  case Criterium::DiscNumber:
    return "Disc Number";

  default:
    assert(false && "Missing case statement");
  }

  return QString();
}

QString SmartPlaylistQuery::ToUiString(Comparison c)
{
  switch (c)
  {
  case Comparison::Equal:
    return "=";
  case Comparison::NotEqual:
    return "<>";
  case Comparison::Less:
    return "<";
  case Comparison::LessEqual:
    return "<=";
  case Comparison::Greater:
    return ">";
  case Comparison::GreaterEqual:
    return ">=";
  case Comparison::Is:
    return "is";
  case Comparison::IsNot:
    return "is not";
  case Comparison::Contains:
    return "contains";
  case Comparison::ContainsNot:
    return "does not contain";
  case Comparison::StartsWith:
    return "starts with";
  case Comparison::EndsWith:
    return "ends with";
  case Comparison::IsUnknown:
    return "is unknown";

  default:
    assert(false && "Missing case statement");
  }

  return QString();
}

QString SmartPlaylistQuery::ToUiString(SortOrder so)
{
  switch (so)
  {
  case SortOrder::Random:
    return "Random";
  case SortOrder::ArtistAtoZ:
    return "Artist";
  case SortOrder::AlbumAtoZ:
    return "Album";
  case SortOrder::TitleAtoZ:
    return "Title";
  case SortOrder::YearNewToOld:
    return "Year (oldest first)";
  case SortOrder::YearOldToNew:
    return "Year (newest first)";
  case SortOrder::DurationShortToLong:
    return "Duration (shortest first)";
  case SortOrder::DurationLongToShort:
    return "Duration (longest first)";
  case SortOrder::PlayDateOld:
    return "Last Played (oldest first)";
  case SortOrder::PlayDataNew:
    return "Last Played (recent first)";
  case SortOrder::PlayCountLow:
    return "Play Count (least to most)";
  case SortOrder::PlayCountHigh:
    return "Play Count (most to least)";
  case SortOrder::RatingLow:
    return "Rating (low to high)";
  case SortOrder::RatingHigh:
    return "Rating (high to low)";
  case SortOrder::DateAddedNew:
    return "Date Added (newest first)";
  case SortOrder::DateAddedOld:
    return "Date Added (oldest first)";

  default:
    assert(false && "Missing case statement");
  }

  return QString();
}

bool SmartPlaylistQuery::AdjustComparison(Criterium crit, Comparison& inout_Compare)
{
  std::vector<Comparison> allowed;
  GetAllowedComparisons(crit, allowed);

  for (size_t i = 0; i < allowed.size(); ++i)
  {
    if (allowed[i] == inout_Compare)
      return false;
  }

  inout_Compare = allowed[0];
  return true;
}

QString SmartPlaylistQuery::ToDbString(Criterium c)
{
  switch (c)
  {
  case Criterium::Artist:
    return "artist";

  case Criterium::Album:
    return "album";

  case Criterium::Title:
    return "title";

  case Criterium::Year:
    return "year";

  case Criterium::TrackNumber:
    return "track";

  case Criterium::Length:
    return "length";

  case Criterium::Rating:
    return "rating";

  case Criterium::LastPlayed:
    return "lastplayed";

  case Criterium::DateAdded:
    return "dateadded";

  case Criterium::PlayCount:
    return "playcount";

  case Criterium::DiscNumber:
    return "disc";

  default:
    assert(false && "Missing case statement");
  }

  return QString();
}

QString SmartPlaylistQuery::ToDbString(Comparison c, const QString& value)
{
  char tmp[256];
  sqlite3_snprintf(255, tmp, "%q", value.toUtf8().data());

  switch (c)
  {
  case Comparison::Equal:
    return QString(" = %1").arg(tmp);

  case Comparison::NotEqual:
    return QString(" <> %1").arg(tmp);

  case Comparison::Less:
    return QString(" < %1").arg(tmp);

  case Comparison::LessEqual:
    return QString(" <= %1").arg(tmp);

  case Comparison::Greater:
    return QString(" > %1").arg(tmp);

  case Comparison::GreaterEqual:
    return QString(" >= %1").arg(tmp);

  case Comparison::Is:
    return QString(" = '%1'").arg(tmp);

  case Comparison::IsNot:
    return QString(" != '%1'").arg(tmp);

  case Comparison::Contains:
    return QString(" LIKE '%%%1%%'").arg(tmp);

  case Comparison::ContainsNot:
    return QString(" NOT LIKE '%%%1%%'").arg(tmp);

  case Comparison::StartsWith:
    return QString(" LIKE '%1%%'").arg(tmp);

  case Comparison::EndsWith:
    return QString(" LIKE '%%%1'").arg(tmp);

  case Comparison::IsUnknown:
    return QString(" IS NULL");

  default:
    assert(false && "Missing case statement");
  }

  return QString();
}

QString SmartPlaylistQuery::GenerateSQL() const
{
  return m_MainGroup.GenerateSQL();
}

QString SmartPlaylistQuery::GenerateOrderBySQL() const
{
  switch (m_SortOrder)
  {
  case SmartPlaylistQuery::SortOrder::Random:
    return QString();
  case SmartPlaylistQuery::SortOrder::ArtistAtoZ:
    return "artist, album, disc, track";
  case SmartPlaylistQuery::SortOrder::AlbumAtoZ:
    return "album, disc, track";
  case SmartPlaylistQuery::SortOrder::TitleAtoZ:
    return "title";
  case SmartPlaylistQuery::SortOrder::YearNewToOld:
    return "year ASC, artist, album, disc, track";
  case SmartPlaylistQuery::SortOrder::YearOldToNew:
    return "year DESC, artist, album, disc, track";
  case SmartPlaylistQuery::SortOrder::DurationShortToLong:
    return "length ASC";
  case SmartPlaylistQuery::SortOrder::DurationLongToShort:
    return "length DESC";
  case SmartPlaylistQuery::SortOrder::PlayDateOld:
    return "lastplayed ASC";
  case SmartPlaylistQuery::SortOrder::PlayDataNew:
    return "lastplayed DESC";
  case SmartPlaylistQuery::SortOrder::PlayCountLow:
    return "playcount ASC, artist, album, disc, track";
  case SmartPlaylistQuery::SortOrder::PlayCountHigh:
    return "playcount DESC, artist, album, disc, track";
  case SmartPlaylistQuery::SortOrder::RatingLow:
    return "rating ASC, artist, album, disc, track";
  case SmartPlaylistQuery::SortOrder::RatingHigh:
    return "rating DESC, artist, album, disc, track";
  case SmartPlaylistQuery::SortOrder::DateAddedOld:
    return "dateadded ASC, artist, album, disc, track";
  case SmartPlaylistQuery::SortOrder::DateAddedNew:
    return "dateadded DESC, artist, album, disc, track";

  default:
    assert(false && "Missing case statement");
  }

  return QString();
}

QString SmartPlaylistQuery::ConditionGroup::GenerateSQL() const
{
  QString result;
  QString conc = m_Fulfil == Fulfil::All ? " AND " : " OR ";

  for (auto it = m_SubGroups.begin(); it != m_SubGroups.end(); ++it)
  {
    QString sub = it->GenerateSQL();

    if (sub.isEmpty())
      continue;

    if (!result.isEmpty())
      result += conc;

    result += "(" + sub + ")";
  }

  for (auto it = m_Statements.begin(); it != m_Statements.end(); ++it)
  {
    QString sub = it->GenerateSQL();

    if (sub.isEmpty())
      continue;

    if (!result.isEmpty())
      result += conc;

    result += "(" + sub + ")";
  }

  return result;
}

QString SmartPlaylistQuery::Statement::GenerateSQL() const
{
  QString result;

  result += ToDbString(m_Criterium);
  result += ToDbString(m_Compare, m_Value);

  return result;
}

void SmartPlaylistQuery::Save(QDataStream& stream) const
{
  const int version = 1;
  stream << version;

  stream << m_iSongLimit;
  stream << (int)m_SortOrder;

  Save(stream, m_MainGroup);
}

void SmartPlaylistQuery::Save(QDataStream& stream, const ConditionGroup& group) const
{
  stream << (int)group.m_Fulfil;

  int numSt = (int)group.m_Statements.size();
  stream << numSt;

  for (auto it = group.m_Statements.begin(); it != group.m_Statements.end(); ++it)
  {
    const auto& st = *it;

    stream << (int)st.m_Criterium;
    stream << (int)st.m_Compare;
    stream << st.m_Value;
  }

  const int numGr = (int)group.m_SubGroups.size();
  stream << numGr;

  for (auto it = group.m_SubGroups.begin(); it != group.m_SubGroups.end(); ++it)
  {
    const auto& gr = *it;

    Save(stream, gr);
  }
}

void SmartPlaylistQuery::Load(QDataStream& stream)
{
  int version = 0;
  stream >> version;

  if (version != 1)
    return;

  stream >> m_iSongLimit;

  int sortOrder = 0;
  stream >> sortOrder;
  m_SortOrder = (SortOrder)sortOrder;

  Load(stream, m_MainGroup);
}

void SmartPlaylistQuery::Load(QDataStream& stream, ConditionGroup& group)
{
  int fulfil = 0;
  stream >> fulfil;
  group.m_Fulfil = (Fulfil)fulfil;

  int numSt = 0;
  stream >> numSt;

  for (int i = 0; i < numSt; ++i)
  {
    Statement st;

    int crit = 0;
    stream >> crit;
    st.m_Criterium = (Criterium)crit;

    int comp = 0;
    stream >> comp;
    st.m_Compare = (Comparison)comp;

    stream >> st.m_Value;

    group.m_Statements.push_back(st);
  }

  int numGr = 0;
  stream >> numGr;

  for (int i = 0; i < numGr; ++i)
  {
    ConditionGroup gr;
    Load(stream, gr);

    group.m_SubGroups.push_back(gr);
  }
}
