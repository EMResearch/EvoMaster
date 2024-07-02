package org.evomaster.client.java.instrumentation.coverage.methodreplacement;

import org.evomaster.client.java.instrumentation.coverage.methodreplacement.classes.*;
import org.evomaster.client.java.instrumentation.coverage.methodreplacement.thirdpartyclasses.*;
import org.evomaster.client.java.instrumentation.shared.ClassName;
import org.evomaster.client.java.utils.SimpleLogger;

import java.util.*;
import java.util.stream.Collectors;

public class ReplacementList {

    private static List<MethodReplacementClass> listCache;

    /*
        FIXME why was this a list of instances and not a list of Class<? extends MethodReplacementClass> ???
     */

    /**
     * Return all the available method replacement classes.
     * Every time a new replacement class is implemented, it needs to
     * be manually added to this list.
     */
    public static List<MethodReplacementClass> getList() {

        if(listCache == null) {
            listCache = Arrays.asList(
                    new AbstractEndpointClassReplacement(),
                    new Base64DecoderClassReplacement(),
                    new BooleanClassReplacement(),
                    new ByteClassReplacement(),
                    new CharacterClassReplacement(),
                    new CollectionClassReplacement(),
                    new CursorPreparerClassReplacement(),
                    new DateClassReplacement(),
                    new DateFormatClassReplacement(),
                    new DoubleClassReplacement(),
                    new EnumClassReplacement(),
                    new FloatClassReplacement(),
                    new GsonClassReplacement(),
                    new Http11ProcessorReplacementClass(),
                    new HttpServletRequestClassReplacement(),
                    new IntegerClassReplacement(),
                    new InetAddressClassReplacement(),
                    new InetSocketAddressClassReplacement(),
                    new JacksonObjectMapperClassReplacement(),
                    new LocalDateClassReplacement(),
                    new LocalDateTimeClassReplacement(),
                    new LocalTimeClassReplacement(),
                    new LongClassReplacement(),
                    new MapClassReplacement(),
                    new MatcherClassReplacement(),
                    new MessageBodyReaderClassReplacement(),
                    new MethodClassReplacement(),
                    new MongoCollectionClassReplacement(),
                    new MappingMongoEntityInformationClassReplacement(),
                    new OkHttpClient3BuilderClassReplacement(),
                    new OkHttpClient3ClassReplacement(),
                    new OkHttpClientClassReplacement(),
                    new OkUrlFactoryClassReplacement(),
                    new ObjectClassReplacement(),
                    new ObjectIdClassReplacement(),
                    new ObjectsClassReplacement(),
                    new PatternClassReplacement(),
                    new PreparedStatementClassReplacement(),
                    new ProviderBaseClassReplacement(),
                    new StatementClassReplacement(),
                    new StringClassReplacement(),
                    new ShortClassReplacement(),
                    new ServletRequestClassReplacement(),
                    new SocketClassReplacement(),
                    new ThreadMethodReplacement(),
                    new URIClassReplacement(),
                    new URLClassReplacement(),
                    new UUIDClassReplacement(),
                    new ValidatorClassReplacement(),
                    new WebRequestClassReplacement()
                    /* Note: Add new class replacements only in alphabetic order */
            );

            /*
                This can happen if we use a method replacement for a third-party library we shade.
                Note: this will not be detectable in our current E2E tests that do not build the
                JAR file of client with Maven first.
             */
            List<String> shaded = listCache.stream().map(c -> c.getTargetClassName())
                    .filter(n -> n.startsWith("shaded."))
                    .collect(Collectors.toList());
            if(shaded.size() > 0){
                throw new IllegalStateException("Shaded dependencies ended up in the ReplacementList: "
                + String.join(",", shaded));
            }
        }

        return listCache;
    }


    public static List<MethodReplacementClass> getReplacements(String target) {
        return getReplacements(target, false);
    }


    public static List<MethodReplacementClass> getReplacements(String target, boolean strict) {
        Objects.requireNonNull(target);
        final String targetClassName = ClassName.get(target).getFullNameWithDots();

        List<MethodReplacementClass> list = getList().stream()
                //.filter(t -> t.isAvailable()) // bad idea to load 3rd classes at this point...
                .filter(t -> {

                    if(strict){
                        //exact match, no subclasses allowed
                        return t.getTargetClassName().equals(targetClassName);
                    }

                    /*
                        TODO: this is tricky, due to how "super" calls are
                        handled. For now, we just allow subclasses if they
                        are of standard JDK.
                        Furthermore, issues with classloading of non-JDK APIs

                        This gives major issues if class loads other non-JDK classes.
                        This for example happens with SQL stuff possibly loading drivers, eg H2.
                        So we cannot load JDK libraries indiscriminately here.
                    */

//                            boolean jdk = targetClassName.startsWith("java.");

                    if(targetClassName.equals("java.lang.Module")){
                        return false;
                        //this for sure will fail on JDK 8 when using WireMock.
                        // might need more check if it happens for other classes as well
                        // See try/catch below here
                        // Note: the if statement here is just to avoid flooding the logs...
                    }

                            //TODO based on actual packages used in the list
                            Set<String> prefixes = new HashSet<>();
                            prefixes.add("java.lang.");
                            prefixes.add("java.util.");
                            prefixes.add("java.time.");

                            prefixes.add("java.net.");

                            //we don't just use java.sql. as that seems to give issue (see previous comments)
                            prefixes.add("java.sql.Statement");
                            prefixes.add("java.sql.CallableStatement");
                            prefixes.add("java.sql.PreparedStatement");

                            boolean jdk = prefixes.stream().anyMatch(k -> targetClassName.startsWith(k)) &&
                                        prefixes.stream().anyMatch(k -> t.getTargetClassName().startsWith(k));

                            if (jdk) {
                                Class<?> klass;
                                try {
                                    klass = Class.forName(targetClassName);
                                } catch (Exception e) {
                                    /*
                                        This can, and does happen, when libraries refer to classes after JDK 8,
                                        and have internal logic to do not crash when running on JDK 8.
                                        This is the case for WireMock trying to load java.lang.Module, by first
                                        checking the JDK version.
                                        But this does not work here when loading the classes directly
                                     */
                                    SimpleLogger.warn("Cannot load JDK class " + targetClassName);
                                    //throw new RuntimeException(e);
                                    return false;
                                }
                                return t.getTargetClass(ReplacementList.class.getClassLoader()).isAssignableFrom(klass);
                            }

                            return t.getTargetClassName().equals(targetClassName);
                        }
                )
                .collect(Collectors.toList());

        assert !strict || (list.size() <= 1); //if strict, at most 1 class can match

        return list;
    }
}
