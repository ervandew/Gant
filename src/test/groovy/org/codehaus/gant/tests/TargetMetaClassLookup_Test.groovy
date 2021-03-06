//  Gant -- A Groovy way of scripting Ant tasks.
//
//  Copyright © 2006–2010, 2013  Russel Winder
//
//  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software distributed under the License is
//  distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
//  implied. See the License for the specific language governing permissions and limitations under the
//  License.

package org.codehaus.gant.tests

/**
 *  A test to ensure that the targets method lookup works.
 *
 *  @author Russel Winder <russel@winder.org.uk>
 */
final class TargetMetaClassLookup_Test extends GantTestCase {
  private final something = 'something'
  private final message = 'message'
  void setUp() {
    super.setUp()
    script = """
includeTargets << gant.targets.Clean
cleanPattern << '**/*~'
target(${something}: '') { ant.echo(message: '${message}') }
setDefaultTarget(${something})
"""
  }

  //  It seems that the same gant.targets.Clean instance is used for all tests in this class which is a bit
  //  sad because it means that there is an accumulation of **/*~ patterns, one for each test method as
  //  addCleanPattern gets executed for each test.  So it is crucial to know when testClean is run to know
  //  what the output will be.  Put it first in the hope it will be run first.

  void testClean() {
    //  Have to do this dry run or the result is indeterminate.
    assertEquals(0, gant.processArgs(['-n', '-f',  '-' , 'clean'] as String[]))
    assertEquals(resultString('clean', '''   [delete] quiet : 'false'
  [fileset] dir : '.', includes : '**/*~', defaultexcludes : 'false'
'''), output) //// */ Emacs fontlock fixup.
    assertEquals('', error)
  }
  void testDefault() {
    assertEquals(0, processCmdLineTargets())
    assertEquals(resultString(something, "     [echo] ${message}\n"), output)
    assertEquals('', error)
  }
  void testMissingTarget() {
    final missingTarget = 'blah'
    assertEquals(-11, processCmdLineTargets(missingTarget))
    assertEquals('', output)
    assertEquals("Target ${missingTarget} does not exist.\n", error)
  }
  void testSomething() {
    assertEquals(0, processCmdLineTargets(something))
    assertEquals(resultString(something, "     [echo] ${message}\n"), output)
    assertEquals('', error)
  }
}
