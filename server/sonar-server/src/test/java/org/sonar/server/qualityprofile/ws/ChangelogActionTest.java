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
package org.sonar.server.qualityprofile.ws;

import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sonar.api.resources.Languages;
import org.sonar.api.utils.System2;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.qualityprofile.QProfileChangeQuery;
import org.sonar.db.rule.RuleTesting;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.qualityprofile.QProfileFactory;
import org.sonar.server.qualityprofile.QProfileRef;
import org.sonar.server.qualityprofile.QProfileTesting;
import org.sonar.server.ws.WsTester;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonar.api.utils.DateUtils.parseDate;
import static org.sonar.server.qualityprofile.QProfileRef.PARAM_PROFILE_KEY;
import static org.sonar.server.qualityprofile.QProfileTesting.XOO_P1_KEY;
import static org.sonar.server.qualityprofile.ws.ChangelogAction.PARAM_SINCE;
import static org.sonar.server.qualityprofile.ws.ChangelogAction.PARAM_TO;

public class ChangelogActionTest {

  private static final long A_DATE = 1_500_000_000_000L;

  @Rule
  public DbTester dbTester = DbTester.create(System2.INSTANCE);

  private WsTester wsTester;
  private ChangelogLoader changelogLoader = mock(ChangelogLoader.class);
  private QProfileFactory profileFactory = mock(QProfileFactory.class);

  @Before
  public void before() {
    wsTester = new WsTester(new QProfilesWs(mock(RuleActivationActions.class), mock(BulkRuleActivationActions.class),
      new ChangelogAction(changelogLoader, profileFactory, new Languages(), dbTester.getDbClient())));
  }

  @Test
  public void changelog_empty() throws Exception {
    when(profileFactory.find(any(DbSession.class), eq(QProfileRef.fromKey(XOO_P1_KEY)))).thenReturn(QProfileTesting.newXooP1());
    when(changelogLoader.load(any(DbSession.class), any(QProfileChangeQuery.class))).thenReturn(new ChangelogLoader.Changelog(0, Collections.emptyList()));

    wsTester.newGetRequest(QProfilesWs.API_ENDPOINT, "changelog").setParam(PARAM_PROFILE_KEY, XOO_P1_KEY)
      .execute().assertJson(getClass(), "changelog_empty.json");
  }

  @Test
  public void changelog_nominal() throws Exception {
    when(profileFactory.find(any(DbSession.class), eq(QProfileRef.fromKey(XOO_P1_KEY)))).thenReturn(QProfileTesting.newXooP1());
    ChangelogLoader.Change change1 = new ChangelogLoader.Change("C1", "ACTIVATED", A_DATE, null, null, null, null, null, null);
    ChangelogLoader.Change change2 = new ChangelogLoader.Change("C2", "ACTIVATED", A_DATE + 10, null, null, null, null, null, null);
    List<ChangelogLoader.Change> changes = asList(change1, change2);
    when(changelogLoader.load(any(DbSession.class), any(QProfileChangeQuery.class))).thenReturn(new ChangelogLoader.Changelog(10, changes));

    wsTester.newGetRequest(QProfilesWs.API_ENDPOINT, "changelog").setParam(PARAM_PROFILE_KEY, XOO_P1_KEY)
      .execute().assertJson(getClass(), "changelog_nominal.json");
  }

  @Test
  public void changelog_with_all_fields() throws Exception {
    when(profileFactory.find(any(DbSession.class), eq(QProfileRef.fromKey(XOO_P1_KEY)))).thenReturn(QProfileTesting.newXooP1());
    ChangelogLoader.Change change1 = new ChangelogLoader.Change("C1", "ACTIVATED", A_DATE, "MAJOR", "marcel", "Marcel", "INHERITED", RuleTesting.XOO_X1, "X One");
    change1.getParams().put("foo", "foo_value");
    change1.getParams().put("bar", "bar_value");
    List<ChangelogLoader.Change> changes = asList(change1);
    when(changelogLoader.load(any(DbSession.class), any(QProfileChangeQuery.class))).thenReturn(new ChangelogLoader.Changelog(10, changes));

    wsTester.newGetRequest(QProfilesWs.API_ENDPOINT, "changelog").setParam(PARAM_PROFILE_KEY, XOO_P1_KEY)
      .execute().assertJson(getClass(), "changelog_full.json");
  }

  @Test
  public void changelog_inclusive_for_dates() throws Exception {
    when(profileFactory.find(any(DbSession.class), eq(QProfileRef.fromKey(XOO_P1_KEY)))).thenReturn(QProfileTesting.newXooP1());
    when(changelogLoader.load(any(DbSession.class), any(QProfileChangeQuery.class))).thenReturn(new ChangelogLoader.Changelog(0, Collections.emptyList()));

    wsTester.newGetRequest(QProfilesWs.API_ENDPOINT, "changelog")
      .setParam(PARAM_PROFILE_KEY, XOO_P1_KEY)
      .setParam(PARAM_SINCE, "2016-09-01")
      .setParam(PARAM_TO, "2016-09-01")
      .execute();

    ArgumentCaptor<QProfileChangeQuery> argumentCaptor = ArgumentCaptor.forClass(QProfileChangeQuery.class);
    verify(changelogLoader).load(any(DbSession.class), argumentCaptor.capture());
    assertThat(argumentCaptor.getValue().getFromIncluded()).isEqualTo(parseDate("2016-09-01").getTime());
    assertThat(argumentCaptor.getValue().getToExcluded()).isEqualTo(parseDate("2016-09-02").getTime());
  }

  @Test(expected = NotFoundException.class)
  public void fail_on_unknown_profile() throws Exception {
    when(profileFactory.find(any(DbSession.class), eq(QProfileRef.fromKey(XOO_P1_KEY)))).thenThrow(new NotFoundException("Profile not found"));

    wsTester.newGetRequest(QProfilesWs.API_ENDPOINT, "changelog").setParam(PARAM_PROFILE_KEY, XOO_P1_KEY).execute();
  }
}
