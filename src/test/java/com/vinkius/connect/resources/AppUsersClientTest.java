/*
 * Copyright (c) Vinkius
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.vinkius.connect.resources;

import static org.assertj.core.api.Assertions.assertThat;

import com.vinkius.connect.fluent.Vinkius;
import com.vinkius.connect.helpers.FakeHttpSender;
import com.vinkius.connect.helpers.FakeHttpSender.MockResponse;
import com.vinkius.connect.helpers.FakeHttpSender.RecordedCall;
import com.vinkius.connect.helpers.FakeHttpSender.Route;
import com.vinkius.connect.helpers.Fixtures;
import com.vinkius.connect.helpers.MockSdk;
import com.vinkius.connect.model.AppUser;
import com.vinkius.connect.model.Paginated;
import com.vinkius.connect.model.RequestOptions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Low-level list, filter, and iterate paths of the app-users resource. */
class AppUsersClientTest {

  private static Map<String, Object> meta(int page, int lastPage) {
    return Map.of(
        "current_page",
        page,
        "from",
        1,
        "last_page",
        lastPage,
        "path",
        "/",
        "per_page",
        50,
        "to",
        1,
        "total",
        2);
  }

  private static Map<String, Object> pageWith(
      String id, String externalId, int page, int lastPage) {
    return Map.of(
        "data",
        List.of(Fixtures.appUser(id, externalId)),
        "meta",
        meta(page, lastPage),
        "links",
        Map.of());
  }

  private static FakeHttpSender senderForTwoPages() {
    return FakeHttpSender.of(
        Route.get(
            "^/apps/vk_app_test/users$",
            call -> {
              String page = call.queryParam("page") != null ? call.queryParam("page") : "1";
              return MockResponse.ok(
                  "2".equals(page) ? pageWith("u2", "b", 2, 2) : pageWith("u1", "a", 1, 2));
            }));
  }

  @Test
  void listAppliesStatusAndPageFilters() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.get(
                "^/apps/vk_app_test/users$", call -> MockResponse.ok(pageWith("u1", "a", 2, 2))));
    Vinkius vinkius = MockSdk.make(sender);

    Paginated<AppUser> result = vinkius.users().list("active", 2, RequestOptions.EMPTY);

    assertThat(result.data()).hasSize(1);
    RecordedCall call = sender.call(0);
    assertThat(call.queryParam("status")).isEqualTo("active");
    assertThat(call.queryParam("page")).isEqualTo("2");
  }

  @Test
  void listOverloadOmitsFilterQueryParams() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.get(
                "^/apps/vk_app_test/users$", call -> MockResponse.ok(pageWith("u1", "a", 1, 1))));
    Vinkius vinkius = MockSdk.make(sender);

    vinkius.users().list();
    vinkius.users().list(RequestOptions.EMPTY);

    assertThat(sender.callCount()).isEqualTo(2);
    assertThat(sender.call(0).queryParam("status")).isNull();
    assertThat(sender.call(0).queryParam("page")).isNull();
    assertThat(sender.call(1).queryParam("status")).isNull();
    assertThat(sender.call(1).queryParam("page")).isNull();
  }

  @Test
  void iterateAllCollectsEveryPageWithStatusFilter() {
    FakeHttpSender sender = senderForTwoPages();
    Vinkius vinkius = MockSdk.make(sender);

    List<AppUser> all = vinkius.users().iterateAll("active", RequestOptions.EMPTY);

    assertThat(all).extracting(AppUser::id).containsExactly("u1", "u2");
    assertThat(sender.call(0).queryParam("status")).isEqualTo("active");
    assertThat(sender.call(1).queryParam("status")).isEqualTo("active");
    assertThat(sender.call(1).queryParam("page")).isEqualTo("2");
  }
}
