/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.listeners

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.front50.config.ChaosMonkeyEventListenerConfigurationProperties
import com.netflix.spinnaker.front50.events.ApplicationPermissionEventListener
import com.netflix.spinnaker.front50.model.application.Application
import com.netflix.spinnaker.front50.model.application.ApplicationDAO
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ChaosMonkeyApplicationPermissionEventListenerSpec extends Specification {
  private final static String CHAOS_MONKEY_PRINCIPAL = "chaosmonkey@example.com"

  def applicationDao = Mock(ApplicationDAO)

  @Subject
  def subject = new ChaosMonkeyApplicationPermissionEventListener(
    applicationDao,
    new ChaosMonkeyEventListenerConfigurationProperties(
      userRole: CHAOS_MONKEY_PRINCIPAL,
      enabled: true
    ),
    new ObjectMapper()
  )

  @Unroll
  void "supports application permission events"() {
    expect:
    subject.supports(type) == expectedSupport

    where:
    type                                                || expectedSupport
    ApplicationPermissionEventListener.Type.PRE_CREATE  || true
    ApplicationPermissionEventListener.Type.PRE_UPDATE  || true
    ApplicationPermissionEventListener.Type.PRE_DELETE  || false
    ApplicationPermissionEventListener.Type.POST_CREATE || false
    ApplicationPermissionEventListener.Type.POST_UPDATE || false
    ApplicationPermissionEventListener.Type.POST_DELETE || false
  }

  @Unroll
  void "should add chaos monkey permissions during application permission update"() {
    given:
    Application application = new Application(name: "hello")
    application.details = [
      "chaosMonkey": [
        "enabled": chaosMonkeyEnabled
      ]
    ]

    Application.Permission permission = new Application.Permission(
      name: "hello",
      lastModifiedBy: "bird person",
      lastModified: -1L,
      permissions: new Permissions.Builder()
        .add(Authorization.READ, readPermissions)
        .add(Authorization.WRITE, writePermissions)
        .build()
    )

    Application.Permission updatedPermissions = new Application.Permission(
      name: "hello",
      lastModifiedBy: "bird person",
      lastModified: -1L,
      permissions: new Permissions.Builder()
        .add(Authorization.READ, readPermissionsExpected)
        .add(Authorization.WRITE, writePermissionsExpected)
        .build()
    )

    when:
    1 * applicationDao.findByName(_) >> new Application(details:["chaosMonkey":[enabled:chaosMonkeyEnabled]])
    subject.call(permission, permission)

    then:
    permission.getPermissions() == updatedPermissions.getPermissions()

    where:
    chaosMonkeyEnabled | readPermissions                                 | writePermissions                                | readPermissionsExpected       | writePermissionsExpected
    true               | ["a"]                                           | ["b"]                                           | ["a", CHAOS_MONKEY_PRINCIPAL] | ["b", CHAOS_MONKEY_PRINCIPAL]
    true               | [CHAOS_MONKEY_PRINCIPAL]                        | ["a"]                                           | []                            | ["a", CHAOS_MONKEY_PRINCIPAL]
    true               | ["a", CHAOS_MONKEY_PRINCIPAL]                   | ["b", CHAOS_MONKEY_PRINCIPAL]                   | ["a", CHAOS_MONKEY_PRINCIPAL] | ["b", CHAOS_MONKEY_PRINCIPAL]
    true               | ["a"]                                           | [CHAOS_MONKEY_PRINCIPAL]                        | ["a", CHAOS_MONKEY_PRINCIPAL] | []
    true               | [CHAOS_MONKEY_PRINCIPAL]                        | [CHAOS_MONKEY_PRINCIPAL]                        | []                            | []
    true               | [CHAOS_MONKEY_PRINCIPAL,CHAOS_MONKEY_PRINCIPAL] | [CHAOS_MONKEY_PRINCIPAL,CHAOS_MONKEY_PRINCIPAL] | []                            | []
    true               | [CHAOS_MONKEY_PRINCIPAL]                        | []                                              | []                            | []
    false              | ["a"]                                           | ["b"]                                           | ["a"]                         | ["b"]
    false              | ["a", CHAOS_MONKEY_PRINCIPAL]                   | ["b", CHAOS_MONKEY_PRINCIPAL]                   | ["a"]                         | ["b"]
    false              | [CHAOS_MONKEY_PRINCIPAL]                        | [CHAOS_MONKEY_PRINCIPAL]                        | []                            | []
    false              | [CHAOS_MONKEY_PRINCIPAL,CHAOS_MONKEY_PRINCIPAL] | [CHAOS_MONKEY_PRINCIPAL,CHAOS_MONKEY_PRINCIPAL] | []                            | []
    false              | [CHAOS_MONKEY_PRINCIPAL]                        | []                                              | []                            | []
  }
}
