/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.play.internal.toolchain

import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.tasks.compile.BaseForkOptions
import org.gradle.internal.classpath.ClassPath
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.play.internal.spec.PlayCompileSpec
import org.gradle.workers.internal.WorkerDaemonFactory
import spock.lang.Specification

class DaemonPlayCompilerTest extends Specification {

    def workingDirectory = new File(".").absoluteFile
    def delegate = Mock(Compiler)
    def classpathRegistry = Mock(ClassPathRegistry)
    def workerDaemonFactory = Mock(WorkerDaemonFactory)
    def spec = Mock(PlayCompileSpec)
    def forkOptions = Mock(BaseForkOptions)
    def forkOptionsFactory = TestFiles.execFactory()

    def setup(){
        _ * spec.getForkOptions() >> forkOptions
        _ * forkOptions.jvmArgs >> []
        _ * classpathRegistry.getClassPath(_) >> Stub(ClassPath)
    }

    def "passes compile classpath and packages to daemon options"() {
        given:
        def classpath = someClasspath()
        def packages = ["foo", "bar"]
        def compiler = new DaemonPlayCompiler(workingDirectory, delegate, classpathRegistry, workerDaemonFactory, classpath, packages, forkOptionsFactory)
        when:
        def daemonForkOptions = compiler.toDaemonForkOptions(spec)
        then:
        daemonForkOptions.getClassLoaderStructure().parent.spec.packageNames.containsAll(packages)
        daemonForkOptions.getClassLoaderStructure().parent.parent.spec.classpath.containsAll(classpath.collect {it.toURI().toURL()})
    }

    def "applies fork settings to daemon options"(){
        given:
        def compiler = new DaemonPlayCompiler(workingDirectory, delegate, classpathRegistry, workerDaemonFactory, someClasspath(), [], forkOptionsFactory)
        when:
        1 * forkOptions.getMemoryInitialSize() >> "256m"
        1 * forkOptions.getMemoryMaximumSize() >> "512m"
        then:
        def daemonForkOptions = compiler.toDaemonForkOptions(spec)
        daemonForkOptions.javaForkOptions.getMinHeapSize() == "256m"
        daemonForkOptions.javaForkOptions.getMaxHeapSize() == "512m"
    }

    def someClasspath() {
        [new File("foo.jar"), new File("bar.jar")]
    }
}
