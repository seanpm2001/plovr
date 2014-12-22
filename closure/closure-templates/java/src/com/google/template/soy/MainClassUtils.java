/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.template.soy.SoyFileSet.Builder;
import com.google.template.soy.base.internal.SoyFileKind;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Private utils for classes with a main() method.
 *
 */
class MainClassUtils {

  private MainClassUtils() {}



  /**
   * OptionHandler for args4j that handles a boolean.
   *
   * <p> The difference between this handler and the default boolean option handler supplied by
   * args4j is that the default one doesn't take any param, so can only be used to turn on boolean
   * flags, but never to turn them off. This implementation allows an optional param value
   * true/false/1/0 so that the user can turn on or off the flag.
   */
  public static class BooleanOptionHandler extends OptionHandler<Boolean> {

    /** {@link OptionHandler#OptionHandler(CmdLineParser,OptionDef,Setter)} */
    public BooleanOptionHandler(
        CmdLineParser parser, OptionDef option, Setter<? super Boolean> setter) {
      super(parser, option, setter);
    }

    @Override public int parseArguments(Parameters params) throws CmdLineException {

      boolean value;
      boolean hasParam;
      try {
        String nextArg = params.getParameter(0);
        if (nextArg.equals("true") || nextArg.equals("1")) {
          value = true;
          hasParam = true;
        } else if (nextArg.equals("false") || nextArg.equals("0")) {
          value = false;
          hasParam = true;
        } else {
          // Next arg is not a param for this flag. No param means set flag to true.
          value = true;
          hasParam = false;
        }
      } catch (CmdLineException e) {
        // No additional args on command line. No param means set flag to true.
        value = true;
        hasParam = false;
      }

      setter.addValue(value);
      return hasParam ? 1 : 0;
    }

    @Override public String getDefaultMetaVariable() {
      return null;
    }
  }


  /**
   * OptionHandler for args4j that handles a comma-delimited list.
   */
  public abstract static class ListOptionHandler<T> extends OptionHandler<T> {

    /** {@link OptionHandler#OptionHandler(CmdLineParser,OptionDef,Setter)} */
    public ListOptionHandler(CmdLineParser parser, OptionDef option, Setter<? super T> setter) {
      super(parser, option, setter);
    }

    /**
     * Parses one item from the list into the appropriate type.
     * @param item One item from the list.
     * @return The object representation of the item.
     */
    public abstract T parseItem(String item);

    @Override public int parseArguments(Parameters params) throws CmdLineException {
      for (String item : params.getParameter(0).split(",")) {
        setter.addValue(parseItem(item));
      }
      return 1;
    }

    @Override public String getDefaultMetaVariable() {
      return "ITEM,ITEM,...";
    }
  }


  /**
   * OptionHandler for args4j that handles a comma-delimited list of strings.
   */
  public static class StringListOptionHandler extends ListOptionHandler<String> {

    /** {@link ListOptionHandler#ListOptionHandler(CmdLineParser,OptionDef,Setter)} */
    public StringListOptionHandler(
        CmdLineParser parser, OptionDef option, Setter<? super String> setter) {
      super(parser, option, setter);
    }

    @Override public String parseItem(String item) {
      return item;
    }
  }


  /**
   * Parses command line flags written with args4j.
   *
   * @param objWithFlags An instance of a class containing args4j flag definitions.
   * @param args The args string to parse.
   * @param usagePrefix The string to prepend to the usage message (when reporting an error).
   * @return The CmdLineParser that was created and used to parse the args (can be used to print
   *     usage text for flags when reporting errors).
   */
  public static CmdLineParser parseFlags(Object objWithFlags, String[] args, String usagePrefix) {

    CmdLineParser cmdLineParser = new CmdLineParser(objWithFlags);
    cmdLineParser.setUsageWidth(100);

    try {
      cmdLineParser.parseArgument(args);

    } catch(CmdLineException cle) {
      exitWithError(cle.getMessage(), cmdLineParser, usagePrefix);
    }

    return cmdLineParser;
  }


  /**
   * Prints an error message and the usage string, and then exits.
   *
   * @param errorMsg The error message to print.
   * @param cmdLineParser The CmdLineParser used to print usage text for flags.
   * @param usagePrefix The string to prepend to the usage message (when reporting an error).
   */
  public static void exitWithError(
      String errorMsg, CmdLineParser cmdLineParser, String usagePrefix) {

    System.err.println("\nError: " + errorMsg + "\n\n");
    System.err.println(usagePrefix);
    cmdLineParser.printUsage(System.err);

    System.exit(1);
  }

  /**
   * Creates a Guice injector that includes the SoyModule, a message plugin module, and maybe
   * additional plugin modules, and maybe additional modules.
   *
   * @param msgPluginModuleName The full class name of the message plugin module. Required.
   * @param pluginModuleNames Comma-delimited list of full class names of additional plugin modules
   *     to include. Optional.
   * @return A Guice injector that includes the SoyModule, the given message plugin module, and the
   *     given additional plugin modules (if any).
   */
  public static Injector createInjector(
      String msgPluginModuleName, @Nullable String pluginModuleNames) {

    List<Module> guiceModules = Lists.newArrayListWithCapacity(2);

    guiceModules.add(new SoyModule());

    checkArgument(msgPluginModuleName != null && msgPluginModuleName.length() > 0);
    guiceModules.add(instantiatePluginModule(msgPluginModuleName));

    if (pluginModuleNames != null && !pluginModuleNames.isEmpty()) {
      for (String pluginModuleName : Splitter.on(',').split(pluginModuleNames)) {
        guiceModules.add(instantiatePluginModule(pluginModuleName));
      }
    }

    return Guice.createInjector(guiceModules);
  }

  /**
   * Creates a Guice injector that includes the SoyModule, a message plugin module, and maybe
   * additional plugin modules, and maybe additional modules.
   *
   * @param pluginModuleNames Comma-delimited list of full class names of additional plugin modules
   *     to include. Optional.
   * @return A Guice injector that includes the SoyModule, the given message plugin module, and the
   *     given additional plugin modules (if any).
   */
  public static Injector createInjector(@Nullable String pluginModuleNames) {

    List<Module> guiceModules = Lists.newArrayListWithCapacity(2);

    guiceModules.add(new SoyModule());

    if (pluginModuleNames != null && !pluginModuleNames.isEmpty()) {
      for (String pluginModuleName : Splitter.on(',').split(pluginModuleNames)) {
        guiceModules.add(instantiatePluginModule(pluginModuleName));
      }
    }

    return Guice.createInjector(guiceModules);
  }

  /**
   * Private helper for createInjector().
   *
   * @param moduleName The name of the plugin module to instantiate.
   * @return A new instance of the specified plugin module.
   */
  private static Module instantiatePluginModule(String moduleName) {

    try {
      return (Module) Class.forName(moduleName).newInstance();

    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Cannot find plugin module \"" + moduleName + "\".", e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Cannot access plugin module \"" + moduleName + "\".", e);
    } catch (InstantiationException e) {
      throw new RuntimeException("Cannot instantiate plugin module \"" + moduleName + "\".", e);
    }
  }


  /**
   * Helper to add srcs and deps Soy files to a SoyFileSet builder. Also does sanity checks.
   * @param sfsBuilder The SoyFileSet builder to add to.
   * @param inputPrefix The input path prefix to prepend to all the file paths.
   * @param srcs The srcs from the --srcs flag. Exactly one of 'srcs' and 'args' must be nonempty.
   * @param args The old-style srcs from the command line (that's how they were specified before we
   *     added the --srcs flag). Exactly one of 'srcs' and 'args' must be nonempty.
   * @param deps The deps from the --deps flag, or empty list if not applicable.
   * @param exitWithErrorFn A function that exits with an error message followed by a usage message.
   */
  public static void addSoyFilesToBuilder(
      Builder sfsBuilder, String inputPrefix, Collection<String> srcs, Collection<String> args,
      Collection<String> deps, Collection<String> indirectDeps,
      Function<String, Void> exitWithErrorFn) {

    if (srcs.size() == 0 && args.size() == 0) {
      exitWithErrorFn.apply("Must provide list of source Soy files (--srcs).");
    }
    if (srcs.size() != 0 && args.size() != 0) {
      exitWithErrorFn.apply(
          "Found source Soy files from --srcs and from args (please use --srcs only).");
    }

    // Create Set versions of each of the arguments, and de-dupe. If something is included as
    // multiple file kinds, we'll keep the strongest one; a file in both srcs and deps will be a
    // src, and one in both deps and indirect_deps will be a dep.
    // TODO(gboyer): Maybe stop supporting old style (srcs from command line args) at some point.
    Set<String> srcsSet = ImmutableSet.<String>builder().addAll(srcs).addAll(args).build();
    Set<String> depsSet = Sets.difference(ImmutableSet.copyOf(deps), srcsSet);
    Set<String> indirectDepsSet = Sets.difference(ImmutableSet.copyOf(indirectDeps),
        Sets.union(srcsSet, depsSet));

    for (String src : srcsSet) {
      sfsBuilder.addWithKind(new File(inputPrefix + src), SoyFileKind.SRC);
    }
    for (String dep : depsSet) {
      sfsBuilder.addWithKind(new File(inputPrefix + dep), SoyFileKind.DEP);
    }
    for (String dep : indirectDepsSet) {
      sfsBuilder.addWithKind(new File(inputPrefix + dep), SoyFileKind.INDIRECT_DEP);
    }
  }

}
