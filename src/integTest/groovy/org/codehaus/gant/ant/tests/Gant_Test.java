//  Gant -- A Groovy way of scripting Ant tasks.
//
//  Copyright © 2008-10 Russel Winder
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

package org.codehaus.gant.ant.tests ;

import java.io.BufferedReader ;
import java.io.File ;
import java.io.IOException ;
import java.io.InputStream ;
import java.io.InputStreamReader ;

import java.util.ArrayList ;
import java.util.List ;

import junit.framework.TestCase ;

import org.apache.tools.ant.BuildException ;
import org.apache.tools.ant.Project ;
import org.apache.tools.ant.ProjectHelper ;

import org.apache.tools.ant.util.StringUtils ;

/**
 *  Unit tests for the Gant Ant task.  In order to test things appropriately this test must be initiated
 *  without any of the Groovy, Gant or related jars in the class path.  Also of course it must be a JUnit
 *  test with no connection to Groovy or Gant.
 *
 *  @author Russel Winder
 */
public class Gant_Test extends TestCase {
  private final String endOfTargetMarker = "------ " ;
  private final String separator = System.getProperty ( "file.separator" ) ;
  private final boolean isWindows = System.getProperty ( "os.name" ).startsWith ( "Windows" ) ;
  private final String locationPrefix = ( "Gradle".equals ( System.getProperty ( "buildFrameworkIdentifier" ) ) ? ".." + separator : "" ) ;
  private final String path ; {
    final StringBuilder sb = new StringBuilder ( ) ;
    sb.append ( "src" ) ;
    sb.append ( separator ) ;
    sb.append ( "test" ) ;
    sb.append ( separator ) ;
    sb.append ( "groovy" ) ;
    sb.append ( separator ) ;
    sb.append ( "org" ) ;
    sb.append ( separator ) ;
    sb.append ( "codehaus" ) ;
    sb.append ( separator ) ;
    sb.append ( "gant" ) ;
    sb.append ( separator ) ;
    sb.append ( "ant" ) ;
    sb.append ( separator ) ;
    sb.append ( "tests" ) ;
    path = sb.toString ( ) ;
  }
  private final String canonicalPath ; {
    try { canonicalPath = ( new File ( locationPrefix + path ) ).getCanonicalPath ( ) ; }
    catch ( final IOException ioe ) { throw new RuntimeException ( "Path calculation failure." , ioe ) ; }
  }
  private final File antFile =  new File ( canonicalPath , "gantTest.xml" ) ;
  private Project project ;
  //  This variable is assigned in the Gant script hence the public static.
  public static String returnValue ;

  @Override protected void setUp ( ) throws Exception {
    super.setUp ( ) ;
    project = new Project ( ) ;
    project.init ( ) ;
    ProjectHelper.configureProject ( project , antFile ) ;
    returnValue = "" ;
  }

  public void testDefaultFileDefaultTarget ( ) {
    project.executeTarget ( "gantTestDefaultFileDefaultTarget" ) ;
    assertEquals ( "A test target in the default file." , returnValue ) ;
  }
  public void testDefaultFileNamedTarget ( ) {
    project.executeTarget ( "gantTestDefaultFileNamedTarget" ) ;
    assertEquals ( "Another target in the default file." , returnValue ) ;
  }
  public void testNamedFileDefaultTarget ( ) {
    project.executeTarget ( "gantTestNamedFileDefaultTarget" ) ;
    assertEquals ( "A test target in the default file." , returnValue ) ;
  }
  public void testNamedFileNamedTarget ( ) {
    project.executeTarget ( "gantTestNamedFileNamedTarget" ) ;
    assertEquals ( "Another target in the default file." , returnValue ) ;
  }
  public void testGantWithParametersAsNestedTags ( ) {
    project.executeTarget ( "gantWithParametersAsNestedTags" ) ;
    assertEquals ( "gant -Dflob=adob -Dburble gantParameters" , returnValue ) ;
  }
  public void testMultipleGantTargets ( ) {
    project.executeTarget ( "gantWithMultipleTargets" ) ;
    assertEquals ( "A test target in the default file.Another target in the default file." , returnValue ) ;
  }
  public void testUnknownTarget ( ) {
    try { project.executeTarget ( "blahBlahBlahBlah" ) ; }
    catch ( final BuildException be ) {
      assertEquals ( "Target \"blahBlahBlahBlah\" does not exist in the project \"Gant Ant Task Test\". " , be.getMessage ( ) ) ;
      return ;
    }
    fail ( "Should have got a BuildException." ) ;
  }
  public void testMissingGantfile ( ) {
    try { project.executeTarget ( "missingGantfile" ) ; }
    catch ( final BuildException be ) {
      assertEquals ( "Gantfile does not exist." , be.getMessage ( ) ) ;
      return ;
    }
    fail ( "Should have got a BuildException." ) ;
  }
  /*
   *  Test for the taskdef-related verify error problem.  Whatever it was supposed to do it passes now,
   *  2008-04-14.
   */
  public void testTaskdefVerifyError ( ) {
    project.executeTarget ( "gantTaskdefVerifyError" ) ;
    assertEquals ( "OK." , returnValue ) ;
  }
  /*
   *  A stream gobbler for the spawned process used by the <code>runAnt</code> method in the following
   *  tests.
   *
   *  @author Russel Winder
   */
  private static final class StreamGobbler implements Runnable {
    private final InputStream is ;
    private final StringBuilder sb ;
    public StreamGobbler ( final InputStream is , final StringBuilder sb ) {
      this.is = is ;
      this.sb = sb ;
    }
    public void run ( ) {
      try {
        final BufferedReader br = new BufferedReader ( new InputStreamReader ( is ) ) ;
        while ( true ) {
          final String line = br.readLine ( ) ;  //  Can throw an IOException hence the try block.
          if ( line == null ) { break ; }
          sb.append ( line ).append ( '\n' ) ;
        }
      }
      catch ( final IOException ignore ) { fail ( "Got an IOException reading a line in the read thread." ) ; }
    }
  }
  /*
   *  Run Ant in a separate process.  Return the standard output and the standard error that results as a
   *  List<String> with two items, item 0 is standard output and item 1 is standard error.
   *
   *  <p>This method assumes that either the environment variable ANT_HOME is set to a complete Ant
   *  installation or that the command ant (ant.bat on Windows) is in the path.</p>
   *
   *  <p>As at 2008-12-06 Canoo CruiseControl runs with GROOVY_HOME set to /usr/local/java/groovy, and
   *  Codehaus Bamboo runs without GROOVY_HOME being set.</p>
   *
   *  @param xmlFile the path to the XML file that Ant is to use.
   *  @param target the target to run, pass "" or null for the default target.
   *  @param expectedReturnCode the return code that the Ant execution should return.
   *  @param withClasspath whether the Ant execution should use the full classpath so as to find all the classes.
   */
  private List<String> runAnt ( final String xmlFile , final String target , final int expectedReturnCode , final boolean withClasspath ) {
    final List<String> command = new ArrayList<String> ( ) ;
    final String antHomeString = System.getenv ( "ANT_HOME" ) ;
    String antCommand ;
    if ( antHomeString != null ) { antCommand = antHomeString + separator + "bin" + separator  + "ant" ; }
    else { antCommand = "ant" ; }
    if ( isWindows ) {
      command.add ( "cmd.exe" ) ;
      command.add ( "/c" ) ;
      antCommand += ".bat" ;
    }
    command.add ( antCommand ) ;
    command.add ( "-f" ) ;
    command.add ( xmlFile ) ;
    if ( withClasspath ) {
      final String classpathString = "Gradle".equals ( System.getProperty ( "buildFrameworkIdentifier" ) )
              ? System.getenv ( "gradleClasspathString" )
              : System.getProperty ( "java.class.path" ) ;
      for ( final String p : classpathString.split ( System.getProperty ( "path.separator" ) ) ) {
        command.add ( "-lib" ) ;
        command.add ( p ) ;
      }
    }
    if ( ( target != null ) && ! target.trim ( ).equals ( "" ) ) { command.add ( target ) ; }
    final ProcessBuilder pb = new ProcessBuilder ( command ) ;
    final StringBuilder outputStringBuilder = new StringBuilder ( ) ;
    final StringBuilder errorStringBuilder = new StringBuilder ( ) ;
    try {
      final Process p = pb.start ( ) ;  //  Could throw an IOException hence the try block.
      final Thread outputGobbler = new Thread ( new StreamGobbler ( p.getInputStream ( ) , outputStringBuilder ) ) ;
      final Thread errorGobbler = new Thread ( new StreamGobbler ( p.getErrorStream ( ) , errorStringBuilder ) ) ;
      outputGobbler.start ( ) ;
      errorGobbler.start ( ) ;
      try { assertEquals ( expectedReturnCode , p.waitFor ( ) ) ; }
      catch ( final InterruptedException ignore ) { fail ( "Got an InterruptedException waiting for the Ant process to finish." ) ; }
      try { outputGobbler.join ( ) ;}
      catch ( final InterruptedException ignore ) { fail ( "Got an InterruptedException waiting for the output gobbler to terminate." ) ; }
      try { errorGobbler.join ( ) ;}
      catch ( final InterruptedException ignore ) { fail ( "Got an InterruptedException waiting for the error gobbler to terminate." ) ; }
      final List<String> returnList = new ArrayList<String> ( ) ;
      returnList.add ( outputStringBuilder.toString ( ) ) ;
      returnList.add ( errorStringBuilder.toString ( ) ) ;
      return returnList ;
    }
    catch ( final IOException ignore ) { fail ( "Got an IOException from starting the process." ) ; }
    //  Keep the compiler happy, it doesn't realize that execution cannot get here -- i.e. that fail is a non-returning function.
    return null ;
  }
  /**
   *  The output due to the targets in commonBits.xml.
   */
  private final String commonTargetsList = "-defineGantTask:\n\n" ;
  /*
   *  Tests stemming from GANT-19 and relating to ensuring the right classpath when loading the Groovyc Ant
   *  task.
   */
  private String createBaseMessage ( ) {
    final StringBuilder sb = new StringBuilder ( ) ;
    sb.append ( "Buildfile: " ) ;
    sb.append ( canonicalPath ).append ( separator ) ;
    sb.append ( "gantTest.xml\n\n" ) ;
    sb.append ( commonTargetsList ) ;
    sb.append ( "gantTestDefaultFileDefaultTarget:\n" ) ;
    return sb.toString ( ) ;
  }
  private String trimTimeFromSuccessfulBuild ( final String message ) {
    return message.replaceFirst ( "Total time: [0-9]*.*" , "" ) ;
  }
  public void testRunningAntFromShellFailsNoClasspath ( ) {
    //  On Windows the ant.bat file always returns zero :-(
    final List<String> result = runAnt ( antFile.getPath ( ) , null , ( isWindows ? 0 : 1 ) , false ) ;
    assert result.size ( ) == 2 ;
    //assertEquals ( createBaseMessage ( ) , result.get ( 0 ) ) ;
    final String errorResult = result.get ( 1 ) ;
    //
    //  TODO :  Correct this test.
    //
    assertTrue ( errorResult.startsWith ( "\nBUILD FAILED\n" ) ) ;
    //assertTrue ( errorResult.contains ( "org.codehaus.groovy.control.MultipleCompilationErrorsException: startup failed" ) ) ;
    //assertTrue ( errorResult.contains ( "build: 15: unable to resolve class org.codehaus.gant.ant.tests.Gant_Test\n @ line 15, column 1.\n" ) ) ;
  }
  public void testRunningAntFromShellSuccessful ( ) {
    final List<String> result = runAnt ( antFile.getPath ( ) , null , 0 , true ) ;
    assert result.size ( ) == 2 ;
    assertEquals ( createBaseMessage ( ) + "test:\n" + endOfTargetMarker + "test\n\nBUILD SUCCESSFUL\n\n", trimTimeFromSuccessfulBuild ( result.get ( 0 ) ) ) ;
    assertEquals ( "" , result.get ( 1 ) ) ;
  }
  /*
   *  The following tests are based on the code presented in email exchanges on the Groovy developer list by
   *  Chris Miles.  cf.  GANT-50.  This assumes that the tests are run from a directory other than this one.
   */
  private final String basedirAntFilePath = locationPrefix + path + separator + "basedir.xml" ;

  private String createMessageStart ( final String target , final String taskName , final boolean extraClassPathDefinition ) {
    final StringBuilder sb = new StringBuilder ( ) ;
    sb.append ( "Buildfile: " ) ;
    sb.append ( canonicalPath ) ; 
    sb.append ( separator ) ;
    sb.append ( "basedir.xml\n     [echo] basedir::ant basedir=" ) ;
    sb.append ( canonicalPath ) ;
    sb.append ( "\n\n-define" ) ;
    sb.append ( taskName ) ;
    sb.append ( "Task:\n\n" ) ;
    sb.append ( target ) ;
    sb.append ( ":\n" ) ;
    return sb.toString ( ) ;
  }
  public void testBasedirInSubdirDefaultProjectForGant ( ) {
    final String target = "defaultProject" ;
    final StringBuilder sb = new StringBuilder ( ) ;
    sb.append ( createMessageStart ( target , "Groovy" , true ) ) ;
    sb.append ( "   [groovy] basedir::groovy basedir=" ) ;
    sb.append ( canonicalPath ) ;
    sb.append ( "\n   [groovy] default:\n   [groovy] \n   [groovy] basedir::gant basedir=" ) ;
    //
    //  Currently a Gant object instantiated in a Groovy task in an Ant script does not inherit the basedir
    //  of the "calling" Ant.  Instead it assumes it is rooted in the process start directory.  According to
    //  GANT-50 this is an error.  The question is to decide whether it is or not.
    //
    //  TODO : Should this be sb.append ( canonicalPath ) ?  cf. GANT-50.
    //
    sb.append ( System.getProperty ( "user.dir" ) ) ;
    //sb.append ( canonicalPath ) ;
    sb.append ( "\n   [groovy] " + endOfTargetMarker + "default\n   [groovy] \n\nBUILD SUCCESSFUL\n\n" ) ;
    final List<String> result = runAnt ( basedirAntFilePath , target , 0 , true ) ;
    assert result.size ( ) == 2 ;
    assertEquals ( sb.toString ( ) , trimTimeFromSuccessfulBuild ( result.get ( 0 ) ) ) ;
    assertEquals ( "" , result.get ( 1 ) ) ;
  }
  public void testBasedirInSubdirExplicitProjectForGant ( ) {
    final String target = "explicitProject" ;
    final StringBuilder sb = new StringBuilder ( ) ;
    sb.append ( createMessageStart ( target , "Groovy" , true ) ) ;
    sb.append ( "   [groovy] basedir::groovy basedir=" ) ;
    sb.append ( canonicalPath ) ;
    //
    //  In this case the instantiated Gant object is connected directly to the Project object instantiated
    //  by Ant and so uses the same basedir.  However it seems that the output (and error) stream are not
    //  routed through the bit of Ant that prefixes the output with the current task name. :-(
    //
    sb.append ( "\ndefault:\nbasedir::gant basedir=" ) ;
    sb.append ( canonicalPath ) ;
    sb.append ( "\n" + endOfTargetMarker + "default\n\nBUILD SUCCESSFUL\n\n" ) ;
    final List<String> result = runAnt ( basedirAntFilePath , target , 0 , true ) ;
    assert result.size ( ) == 2 ;
    assertEquals ( sb.toString ( ) , trimTimeFromSuccessfulBuild ( result.get ( 0 ) ) ) ;
    assertEquals ( "" , result.get ( 1 ) ) ;
  }
  public void testBasedirInSubdirGantTask ( ) {
    final String target = "gantTask" ;
    final StringBuilder sb = new StringBuilder ( ) ;
    sb.append ( createMessageStart ( target , "Gant" , false ) ) ;
    sb.append ( "default:\n     [gant] basedir::gant basedir=" ) ;
    sb.append ( canonicalPath ) ;
    sb.append ( "\n" + endOfTargetMarker + "default\n\nBUILD SUCCESSFUL\n\n" ) ;
    final List<String> result = runAnt ( basedirAntFilePath , target , 0 , true ) ;
    assert result.size ( ) == 2 ;
    assertEquals ( sb.toString ( ) , trimTimeFromSuccessfulBuild ( result.get ( 0 ) ) ) ;
    assertEquals ( "" , result.get ( 1 ) ) ;
  }
  //
  //  Test the GANT-80 issues.
  //
  public void test_GANT_80 ( ) {
    final String antFilePath = canonicalPath + separator + "GANT_80.xml" ;
    final StringBuilder sb = new StringBuilder ( ) ;
    sb.append ( "Buildfile: " ) ;
    sb.append ( antFilePath ) ;
    sb.append ( "\n\n" ) ;
    sb.append ( commonTargetsList ) ;
    sb.append ( "default:\ndefault:\n     [gant] From println.\n     [gant] On standard error.\n     [echo] From ant.echo.\n" + endOfTargetMarker + "default\n\nBUILD SUCCESSFUL\n\n" ) ;
    final List<String> result = runAnt ( antFilePath , null , 0 , true ) ;
    assert result.size ( ) == 2 ;
    assertEquals ( sb.toString ( ) , trimTimeFromSuccessfulBuild ( result.get ( 0 ) ) ) ;
    assertEquals ( "" , result.get ( 1 ) ) ;
  }
  //
  //  Ensure that errors are handled correctly by checking one error return case.
  //
  public void testGantTaskErrorReturn ( ) {
    final File file = new File ( canonicalPath , "testErrorCodeReturns.xml" ) ;
    final String target = "usingGantAntTask" ;
    final StringBuilder sb = new StringBuilder ( ) ;
    sb.append ( "Buildfile: " ) ;
    sb.append ( file.getPath ( ) ) ;
    sb.append ( "\n\n" ) ;
    sb.append ( commonTargetsList ) ;
    sb.append ( target ) ;
    sb.append ( ":\n" ) ;
    final List<String> result = runAnt ( file.getPath ( ) , target , 1 , true ) ;
    assert result.size ( ) == 2 ;
    assertEquals ( sb.toString ( ) , trimTimeFromSuccessfulBuild ( result.get ( 0 ) ) ) ;
    final String errorResult = result.get(1) ;
    assertTrue ( errorResult.startsWith ( "\nBUILD FAILED\n" ) ) ;
    assertTrue ( errorResult.contains ( file.getPath ( ) ) ) ;
    assertTrue ( errorResult.contains ( "Gantfile does not exist." ) ) ;
  }
  /*
   *  For the moment comment this out because there is no guarantee of a Gant installation.
   *
   *  TODO:  Find out how to set up a Gant installation so this can be tested.
   *
  public void testExecOfGantScriptReturnErrorCode ( ) {
    final File file = new File ( path , "testErrorCodeReturns.xml" ) ;
    final String target = "usingExec" ;
    final StringBuilder sb = new StringBuilder ( ) ;
    sb.append ( "Buildfile: " ) ;
    sb.append ( file.getPath ( ) ) ;
    sb.append ( "\n\n" ) ;
    sb.append ( target ) ;
    sb.append ( ":\n     [exec] Cannot open file  nonexistentGantFile.gant\n     [echo] ErrorLevel: 253\n\nBUILD SUCCESSFUL\n\n" ) ;
    final List<String> result = runAnt ( file.getPath ( ) , target , 0 , true ) ;
    assert result.size ( ) == 2 ;
    assertEquals ( sb.toString ( ) , trimTimeFromSuccessfulBuild ( result.get ( 0 ) ) ) ;
    assertEquals ( "     [exec] Result: 253\n" , result.get ( 1 ) ) ;
  }
  */
  //
  //  For dealing with GANT-110 -- thanks to Eric Van Dewoestine for providing the original --
  //  subsequently amended as Gant evolves.
  //
  public void testInheritAll ( ) {
    final List<String> result = runAnt ( antFile.getPath ( ) , "gantTestInheritAll" , 0 , true ) ;
    @SuppressWarnings("unchecked") List<String> output = StringUtils.lineSplit ( result.get ( 0 ) ) ;
    assertEquals ( "     [echo] ${gant.test.inheritAll}" , output.get ( 6 ) ) ;
    assertEquals ( "gantInheritAll:" , output.get ( 8 ) ) ;
    assertEquals ( "     [echo] ${gant.test.inheritAll}" , output.get ( 9 ) ) ;
    assertEquals ( "gantInheritAll:" , output.get ( 11 ) ) ;
    assertEquals ( "     [echo] gantInheritAllWorks" , output.get ( 12 ) ) ;
  }
  //
  //  For dealing with GANT-111 -- thanks to Eric Van Dewoestine for providing the original --
  //  subsequently amended as Gant evolves.
  //
  public void testGantTaskFail ( ) {
    final List<String> result = runAnt ( antFile.getPath ( ) , "gantTestFail" , 1 , true ) ;
    assert result.size ( ) == 2 ;
    //  The path to the build file and the line number in that file are part of the output,
    //  so check only the parts of the output that are guaranteed, i.e. not the line number.
    final String errorMessage = trimTimeFromSuccessfulBuild ( result.get ( 1 ) ) ;
    assertEquals ( "\nBUILD FAILED\n" + canonicalPath + "/gantTest.xml", errorMessage.substring ( 0 , errorMessage.indexOf ( ':' ) ) ) ; 
    assertEquals ( ": test fail message\n\n\n" , errorMessage.substring ( errorMessage.lastIndexOf ( ':' ) ) ) ;
  }
  
}
