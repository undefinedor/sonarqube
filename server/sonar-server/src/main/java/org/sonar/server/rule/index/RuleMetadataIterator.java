/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.sonar.server.rule.index;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.rule.RuleKey;
import org.sonar.db.DatabaseUtils;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.ResultSetIterator;

/**
 * Scrolls over table RULES_METADATA and reads documents to populate the rule extension index type
 */
public class RuleMetadataIterator implements Iterator<RuleExtensionDoc>, AutoCloseable {

  private static final String[] FIELDS = {
    "r.plugin_name",
    "r.plugin_rule_key",
    "rm.organization_uuid",
    "rm.tags"
  };

  private static final String SQL_ALL = "SELECT " + StringUtils.join(FIELDS, ",") + " FROM rules r " +
    "INNER JOIN rules_metadata rm ON rm.rule_id = r.id " +
    "WHERE rm.tags is not null AND rm.tags != ''";
  private static final Splitter TAGS_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();

  private final PreparedStatement stmt;
  private final ResultSetIterator<RuleExtensionDoc> iterator;
  private final boolean avoidTemplates;

  RuleMetadataIterator(DbClient dbClient, DbSession dbSession, boolean avoidTemplates) {
    this.avoidTemplates = avoidTemplates;
    String sql = createSql();
    stmt = dbClient.getMyBatis().newScrollingSelectStatement(dbSession, sql);
    iterator = createIterator();
  }

  private RuleMetadataIteratorInternal createIterator() {
    try {
      setParameters(stmt);
      return new RuleMetadataIteratorInternal(stmt);
    } catch (SQLException e) {
      DatabaseUtils.closeQuietly(stmt);
      throw new IllegalStateException("Fail to prepare SQL request to select all rules", e);
    }
  }

  @Override
  public boolean hasNext() {
    return iterator.hasNext();
  }

  @Override
  public RuleExtensionDoc next() {
    return iterator.next();
  }

  private String createSql() {
    StringBuilder sql = new StringBuilder(SQL_ALL);
    if (avoidTemplates) {
      sql.append(" WHERE r.is_template = ?");
    }
    return sql.toString();
  }

  private void setParameters(PreparedStatement stmt) throws SQLException {
    if (avoidTemplates) {
      stmt.setBoolean(1, false);
    }
  }

  @Override
  public void close() {
    try {
      iterator.close();
    } finally {
      DatabaseUtils.closeQuietly(stmt);
    }
  }

  private static final class RuleMetadataIteratorInternal extends ResultSetIterator<RuleExtensionDoc> {

    public RuleMetadataIteratorInternal(PreparedStatement stmt) throws SQLException {
      super(stmt);
    }

    @Override
    protected RuleExtensionDoc read(ResultSet rs) throws SQLException {
      RuleExtensionDoc doc = new RuleExtensionDoc();

      String ruleKey = rs.getString(1);
      String repositoryKey = rs.getString(2);
      RuleKey key = RuleKey.of(repositoryKey, ruleKey);
      doc.setRuleKey(key);
      doc.setScope(RuleExtensionScope.organization(rs.getString(3)));
      doc.setTags(stringTagsToSet(rs.getString(4)));

      return doc;
    }

    private static Set<String> stringTagsToSet(@Nullable String tags) {
      return ImmutableSet.copyOf(TAGS_SPLITTER.split(tags == null ? "" : tags));
    }
  }
}
