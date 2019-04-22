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

package org.gradle.play.internal.toolchain;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Transformer;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.tasks.compile.BaseForkOptionsConverter;
import org.gradle.api.internal.tasks.compile.daemon.AbstractDaemonCompiler;
import org.gradle.api.tasks.compile.BaseForkOptions;
import org.gradle.internal.Cast;
import org.gradle.internal.FileUtils;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.play.internal.spec.PlayCompileSpec;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.workers.internal.ClassLoaderStructure;
import org.gradle.workers.internal.DaemonForkOptions;
import org.gradle.workers.internal.DaemonForkOptionsBuilder;
import org.gradle.workers.internal.KeepAliveMode;
import org.gradle.workers.internal.WorkerDaemonFactory;

import java.io.File;
import java.util.Collection;

public class DaemonPlayCompiler<T extends PlayCompileSpec> extends AbstractDaemonCompiler<T> {
    private final Iterable<File> targetPlayVersionFiles;
    private final Iterable<String> classLoaderPackages;
    private final JavaForkOptionsFactory forkOptionsFactory;
    private final ClassPathRegistry classPathRegistry;
    private final File daemonWorkingDir;

    public DaemonPlayCompiler(File daemonWorkingDir, Compiler<T> compiler, ClassPathRegistry classPathRegistry, WorkerDaemonFactory workerDaemonFactory, Iterable<File> targetPlayVersionFiles, Iterable<String> classLoaderPackages, JavaForkOptionsFactory forkOptionsFactory) {
        super(compiler, workerDaemonFactory);
        this.classPathRegistry = classPathRegistry;
        this.targetPlayVersionFiles = targetPlayVersionFiles;
        this.classLoaderPackages = classLoaderPackages;
        this.forkOptionsFactory = forkOptionsFactory;
        this.daemonWorkingDir = daemonWorkingDir;
    }

    @Override
    protected DaemonForkOptions toDaemonForkOptions(PlayCompileSpec spec) {
        BaseForkOptions forkOptions = spec.getForkOptions();

        Collection<File> playCompilerFiles = classPathRegistry.getClassPath("PLAY-COMPILER").getAsFiles();

        // Additional stuff the Groovy compiler needs
        Action<FilteringClassLoader.Spec> filterConfiguration = new Action<FilteringClassLoader.Spec>() {
            @Override
            public void execute(FilteringClassLoader.Spec spec) {
                // File Collections
                spec.allowPackage("org.gradle.api.file");
                spec.allowPackage("org.gradle.api.internal.file");
                // Reflection
                spec.allowPackage("org.gradle.internal.reflect");
                // Spec
                spec.allowPackage("org.gradle.api.specs");
                // Util
                spec.allowPackage("org.gradle.util");
                // Various classes
                spec.allowClass(GradleException.class);
                spec.allowClass(Cast.class);
                spec.allowClass(FileUtils.class);
                spec.allowClass(Transformer.class);
            }
        };
        ClassLoaderStructure classLoaderStructure = getCompilerClassLoaderStructure(playCompilerFiles, targetPlayVersionFiles, classLoaderPackages, filterConfiguration);


        JavaForkOptions javaForkOptions = new BaseForkOptionsConverter(forkOptionsFactory).transform(forkOptions);
        javaForkOptions.setWorkingDir(daemonWorkingDir);

        return new DaemonForkOptionsBuilder(forkOptionsFactory)
            .javaForkOptions(javaForkOptions)
            .withClassLoaderStrucuture(classLoaderStructure)
            .keepAliveMode(KeepAliveMode.SESSION)
            .build();
    }
}
