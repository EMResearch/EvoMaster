package org.evomaster.client.java.controller.problem.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.evomaster.client.java.controller.api.dto.auth.AuthenticationDto;
import org.evomaster.client.java.controller.api.dto.CustomizedRequestValueDto;
import org.evomaster.client.java.controller.api.dto.auth.JsonAuthRPCEndpointDto;
import org.evomaster.client.java.controller.api.dto.MockDatabaseDto;
import org.evomaster.client.java.controller.api.dto.problem.rpc.*;
import org.evomaster.client.java.controller.problem.rpc.schema.LocalAuthSetupSchema;
import org.evomaster.client.java.controller.problem.rpc.schema.params.*;
import org.evomaster.client.java.controller.problem.rpc.schema.types.*;
import org.evomaster.client.java.controller.problem.rpc.schema.EndpointSchema;
import org.evomaster.client.java.controller.problem.rpc.schema.InterfaceSchema;
import org.evomaster.client.java.utils.SimpleLogger;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * created by manzhang on 2021/11/4
 */
public class RPCEndpointsBuilder {

    private final static ObjectMapper objectMapper = new ObjectMapper();

    private final static String OBJECT_FLAG = "OBJECT";
    private final static String OBJECT_FLAG_SEPARATOR = ":";

    private final static String PROTOBUF_PACKAGE = "com.google.protobuf";

    private final static String PROTOBUF_BUILDER = "Builder";

    private final static String PROTOBUF_MAP_FIELD_SUFFIX = "Map";

    private final static String PROTOBUF_MAP_SETTER_PREFIX = "putAll";

    private final static String PROTOBUF_LIST_FIELD_SUFFIX  = "List";

    private final static String PROTOBUF_LIST_SETTER_PREFIX  = "addAll";

    private final static String PROTOBUF_INTERFACE_BUILDER_SUFFIX  = "OrBuilder";


    private static String getObjectTypeNameWithFlag(Class<?> clazz, String name, int level) {
        if (isNotCustomizedObject(clazz)) return name;
        if (level < 0)
            return OBJECT_FLAG + OBJECT_FLAG_SEPARATOR + name;
        return OBJECT_FLAG + OBJECT_FLAG_SEPARATOR + name + OBJECT_FLAG_SEPARATOR+ level;
    }

    private static String[] parseObjectTypeFlag(String objectFlag){
        String[] info = objectFlag.split(OBJECT_FLAG_SEPARATOR);
        if (info.length == 3) {
            return info;
        }else
            return null;
    }

    private static boolean isNotCustomizedObject(Class<?> clazz){
        return PrimitiveOrWrapperType.isPrimitiveOrTypes(clazz) || clazz == String.class ||
                clazz == ByteBuffer.class || clazz.isEnum() || clazz.isArray() ||
                List.class.isAssignableFrom(clazz) || Set.class.isAssignableFrom(clazz);

    }

    /**
     * validate CustomizedRequestValueDto, eg,
     * 1) for any CustomizedRequestValueDto, keyValuePairs and keyValues could not be specified or null at the same time
     * 2) for keyValuePairs, if annotationOnEndpoint or specificEndpointName or specificRequestTypeName are specified, they should have consistent keys
     * 3) keyValues with respect to any specific annotationOnEndpoint or specificEndpointName or specificRequestTypeName should be specified only one time
     * @param customizedRequestValueDtos are customized info to be checked
     */
    public static void validateCustomizedValueInRequests(List<CustomizedRequestValueDto> customizedRequestValueDtos){
        if (customizedRequestValueDtos == null || customizedRequestValueDtos.isEmpty()) return;

        customizedRequestValueDtos.forEach(s->{
            if (s.keyValues != null && s.combinedKeyValuePairs != null)
                throw new IllegalArgumentException("Driver Config Error: keyValues and keyValuePairs should not be specified at the same time");
            if (s.keyValues == null && s.combinedKeyValuePairs == null)
                throw new IllegalArgumentException("Driver Config Error: one of keyValues and keyValuePairs must be specified, could not be null at the same time");
        });

        validateKeyValuePairs(customizedRequestValueDtos);
        validateKeyValues(customizedRequestValueDtos);
    }

    /**
     * validate specified notNullAnnotations
     * @param notNullAnnotations are specified customized annotation representing if any field of RPC dto is required
     */
    public static void validateCustomizedNotNullAnnotationForRPCDto(List<CustomizedNotNullAnnotationForRPCDto> notNullAnnotations){
        if (notNullAnnotations == null || notNullAnnotations.isEmpty()) return;
        notNullAnnotations.forEach(s->{
            if (s.annotationType == null)
                throw new IllegalArgumentException("Driver Config Error: annotationType should not be null");
            if ((s.annotationMethod == null) ^ (s.equalsTo == null))
                throw new IllegalArgumentException("Driver Config Error: annotationMethod and equalsTo should be specified at the same time");
        });
    }

    public static void handleExternalResponses(InterfaceSchema schema, SeededRPCActionDto actionDto, RPCType type){
        if (actionDto.mockRPCExternalServiceDtos!= null && !actionDto.mockRPCExternalServiceDtos.isEmpty()){
            for (MockRPCExternalServiceDto dto : actionDto.mockRPCExternalServiceDtos){
                buildExternalServiceResponse(schema, dto, type);
            }
        }
        if (actionDto.mockDatabaseDtos != null && !actionDto.mockDatabaseDtos.isEmpty()){
            for (MockDatabaseDto dto : actionDto.mockDatabaseDtos){
                buildDbExternalServiceResponse(schema, dto, type);
            }
        }
    }

    /**
     * attempt to identify class from the given client
     * @param schema is the interface schema which might request the responses from the external service
     * @param responseType a type to identify
     * @param rpcType  is the rpc type
     */
    private static NamedTypedValue buildExternalServiceResponse(InterfaceSchema schema, String responseType, RPCType rpcType){

        try {
            Class<?> clazz = Class.forName(responseType);
            Map<TypeVariable, Type> genericTypeMap = new HashMap<>();
            return build(schema, clazz, null, "return", rpcType, new ArrayList<>(), 0, null, null, null, null, null, genericTypeMap, true);
        } catch (ClassNotFoundException e) {
            SimpleLogger.recordErrorMessage("Warning: cannot identify the class from the driver "+e.getMessage());
        } catch (Exception e){
            throw new RuntimeException("EM schema parser error: fail to extract mocked response "+ responseType);
        }
        return null;
    }

    /**
     * identify return datatype for API services as external services
     * @return identified datatype
     */
    public static NamedTypedValue buildExternalServiceResponse(InterfaceSchema schema, MockRPCExternalServiceDto apiDto, RPCType rpcType){

        try {
            if (apiDto != null){
                // get info
                Class<?> interfaceClazz = Class.forName(apiDto.interfaceFullName);
                List<Method> methods =
                    Arrays.stream(interfaceClazz.getDeclaredMethods()).filter(m-> m.getName().equals(apiDto.functionName)).collect(Collectors.toList());
                Method method = findMethod(methods, apiDto.inputParameterTypes);
                Map<TypeVariable, Type> genericTypeMap = new HashMap<>();
                NamedTypedValue response= build(schema, method.getReturnType(), method.getGenericReturnType(), "return", rpcType, new ArrayList<>(), 0, null, null, null, null, null, genericTypeMap, true);
                List<String> modifiedTypes = new ArrayList<>(apiDto.responseTypes);
                for (int i = 0; i < modifiedTypes.size(); i ++){
                    if (modifiedTypes.get(i).equals(method.getReturnType().getName())){
                        modifiedTypes.set(i, response.getType().getFullTypeNameWithGenericType());
                    }
                }
                apiDto.responseFullTypesWithGeneric = modifiedTypes;
                return response;
            }
        } catch (ClassNotFoundException e) {
            if (apiDto.responseTypes != null && (!apiDto.responseTypes.isEmpty())){
                for (String responseType : apiDto.responseTypes){
                    if (responseType.length() > 0){
                        buildExternalServiceResponse(schema, responseType, rpcType);
                    }
                }
            }
            return null;
        }
        return null;
    }

    /**
     * identify return datatype for database as external services
     * @return identified datatype
     */
    public static NamedTypedValue buildDbExternalServiceResponse(InterfaceSchema schema, MockDatabaseDto dbDto, RPCType rpcType){

        try {
            if (dbDto != null){

                if(dbDto.commandName == null)
                    throw new IllegalArgumentException("For MockDatabaseDto, its commandName is not nullable");

                int index = dbDto.commandName.lastIndexOf(".");
                if (index > 0){
                    String methodName = dbDto.commandName.substring(index+1);
                    String dbClazzName = dbDto.commandName.substring(0, index);
                    Class<?> dbClazz = Class.forName(dbClazzName);
                    List<Method> methods =
                        Arrays.stream(dbClazz.getDeclaredMethods()).filter(m-> m.getName().equals(methodName)).collect(Collectors.toList());
                    Method method = findMethod(methods, null);
                    if(method == null){
                        throw new IllegalArgumentException("cannot find specified command method:"+dbDto.commandName);
                    }

                    Map<TypeVariable, Type> genericTypeMap = new HashMap<>();
                    NamedTypedValue response = build(schema, method.getReturnType(), method.getGenericReturnType(), "return", rpcType, new ArrayList<>(), 0, null, null, null, null, null, genericTypeMap, true);
                    dbDto.responseFullTypeWithGeneric = response.getType().getFullTypeNameWithGenericType();
                    return response;
                }
            }
        } catch (ClassNotFoundException e) {
            if (dbDto.responseFullType != null && dbDto.responseFullType.length() > 0)
                buildExternalServiceResponse(schema, dbDto.responseFullType, rpcType);
            return null;
        }
        return null;
    }

    private static Method findMethod(List<Method> methods, List<String> inputs){
        if (methods.isEmpty()) return null;
        if (methods.size() == 1 || inputs == null) return methods.get(0);
        return methods.stream().filter(m->
            m.getParameterTypes().length == inputs.size() && Arrays.stream(m.getParameterTypes()).allMatch(fc-> inputs.contains(fc.getName()))
        ).findFirst().orElse(null);
    }

    private static void validateKeyValues(List<CustomizedRequestValueDto> customizedRequestValueDtos){
        List<String> handled = new ArrayList<>();
        customizedRequestValueDtos.stream().filter(s-> s.keyValues !=null).forEach(s->{
            if (s.keyValues.key == null)
                throw new IllegalArgumentException("Driver Config Error: key must be specified when customizing keyValues");
            if (s.keyValues.values.isEmpty()){
                throw new IllegalArgumentException("Driver Config Error: at least one values is needed for customizing keyValues with the key "+s.keyValues.key);
            }
            String key = "key:"+s.keyValues.key+getKeyForCustomizedRequestValueDto(s);
            if (handled.contains(key))
                throw new IllegalArgumentException("Driver Config Error: "+key+" should be specified only once");
            handled.add(key);
        });
    }


    private static void validateKeyValuePairs(List<CustomizedRequestValueDto> customizedRequestValueDtos){
        Map<String, List<CustomizedRequestValueDto>> group = new HashMap<>();
        customizedRequestValueDtos.stream().filter(s-> s.combinedKeyValuePairs !=null && !s.combinedKeyValuePairs.isEmpty()).forEach(s->{

            String key = getKeyForCustomizedRequestValueDto(s);
            if (key.length() != 0){
                if (!group.containsKey(key))
                    group.put(key, new ArrayList<>());
                group.get(key).add(s);
            }
        });

        group.forEach((key, g) -> {
            if (g.size() > 1) {
                List<String> keys = g.get(0).combinedKeyValuePairs.stream().map(a -> a.fieldKey).collect(Collectors.toList());
                g.forEach(a -> {
                    List<String> akeys = a.combinedKeyValuePairs.stream().map(k -> k.fieldKey).collect(Collectors.toList());
                    if (akeys.size() != keys.size() || !akeys.containsAll(keys)) {
                        throw new IllegalArgumentException("Driver Config Error: keys for same " + key + " must be specified with same keys");
                    }
                });
            }
        });
    }

    private static String getKeyForCustomizedRequestValueDto(CustomizedRequestValueDto s){
        String key = "";
        if (s.annotationOnEndpoint != null)
            key += " annotationOnEndpoint_"+s.annotationOnEndpoint;
        if (s.specificEndpointName != null)
            key += " specificEndpointName_"+s.specificEndpointName;
        if (s.specificRequestTypeName != null)
            key += " specificRequestTypeName_"+s.specificRequestTypeName;
        return key;
    }

    /**
     * @param interfaceName the name of interface
     * @param rpcType          is the type of RPC, e.g., gRPC, Thrift
     * @param client is the corresponding client to maniplute the interface
     * @param skipEndpointsByName specifies a list of names of endpoints to be skipped during testing
     * @param skipEndpointsByAnnotation specifies a list of annotations applied on endpoints that could be skipped during testing
     * @param involveEndpointsByName specifies a list of names of endpoints to be involved during testing
     * @param involveEndpointsByAnnotation specifies a list of annotations applied on endpoints that are involved during testing
     * @param authenticationDtoList specifies a list of authentication info
     * @param customizedRequestValueDtos specifies a list of candidate values in requests
     * @return an interface schema for evomaster to access
     */
    public static InterfaceSchema build(String interfaceName, RPCType rpcType, Object client,
                                        List<String> skipEndpointsByName, List<String> skipEndpointsByAnnotation,
                                        List<String> involveEndpointsByName, List<String> involveEndpointsByAnnotation,
                                        List<AuthenticationDto> authenticationDtoList,
                                        List<CustomizedRequestValueDto> customizedRequestValueDtos,
                                        List<CustomizedNotNullAnnotationForRPCDto> notNullAnnotations) {
        List<EndpointSchema> endpoints = new ArrayList<>();
        List<EndpointSchema> endpointsForAuth = new ArrayList<>();
        List<String> skippedEndpoints = new ArrayList<>();
        Map<Integer, EndpointSchema> authEndpoints = new HashMap<>();
        try {
            Class<?> interfaze = Class.forName(interfaceName);
            InterfaceSchema schema = new InterfaceSchema(interfaceName, endpoints, getClientClass(client) , rpcType, skippedEndpoints, authEndpoints, endpointsForAuth);

            for (Method m : interfaze.getDeclaredMethods()) {
                if (filterRPCFunctionMethod(m)){
                    if (filterRPCFunctionMethodBasedOnSpecified(m, skipEndpointsByName, skipEndpointsByAnnotation, involveEndpointsByName, involveEndpointsByAnnotation)){
                        try{
                            EndpointSchema endpointSchema = build(schema, m, rpcType, authenticationDtoList, customizedRequestValueDtos, notNullAnnotations);
                            endpoints.add(endpointSchema);
                        }catch (RuntimeException exception){
                        /*
                            TODO might send such log to core in order to better identify problems which is not handled yet
                         */
                            SimpleLogger.recordErrorMessage("EM Driver Error: fail to handle the endpoint schema "+m.getName()+" with the error msg:"+exception.getMessage());
                        }
                    }else {
                        skippedEndpoints.add(m.getName());
                    }
                }

                List<AuthenticationDto> auths = getAuthEndpointInInterface(authenticationDtoList, interfaceName, m);
                if (auths != null && !auths.isEmpty()){
                    try{
                        // handle endpoint which is for auth setup
                        EndpointSchema authEndpoint = build(schema, m, rpcType, null, customizedRequestValueDtos,notNullAnnotations);
                        endpointsForAuth.add(authEndpoint);

                        for (AuthenticationDto auth: auths){
                            EndpointSchema copy = authEndpoint.copyStructure();
                            if (auth.jsonAuthEndpoint == null){
                                throw new IllegalArgumentException("Driver Config Error: now we only support auth info specified with JsonAuthRPCEndpointDto");
                            }
                            int index = authenticationDtoList.indexOf(auth);

                            // set value based on specified info
                            if (copy.getRequestParams().size() != auth.jsonAuthEndpoint.jsonPayloads.size())
                                throw new IllegalArgumentException("Driver Config Error: mismatched size of jsonPayloads ("+auth.jsonAuthEndpoint.classNames.size()+") with real endpoint ("+authEndpoint.getRequestParams().size()+").");
                            setAuthEndpoint(copy, auth.jsonAuthEndpoint);
                            authEndpoints.put(index, copy);
                        }
                    }catch (RuntimeException exception){
                        SimpleLogger.recordErrorMessage("EM Driver Error: fail to handle the authEndpoint schema "+m.getName()+" with the error msg:"+exception.getMessage());
                    }
                }
            }
            return schema;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("cannot find the interface with the name (" + interfaceName + ") and the error message is " + e.getMessage());
        }
    }

    /**
     * build the local auth setup
     * @param authenticationDtoList a list of auth info specified by user
     * @return a map of such local auth setup
     *      key - index at a list of auth info specified by user
     *      value - local endpoint
     */
    public static Map<Integer, LocalAuthSetupSchema> buildLocalAuthSetup(List<AuthenticationDto> authenticationDtoList){
        if (authenticationDtoList==null || authenticationDtoList.isEmpty()) return null;
        Map<Integer, LocalAuthSetupSchema> map = new HashMap<>();
        for (AuthenticationDto dto : authenticationDtoList){
            if (dto.localAuthSetup != null){
                int index = authenticationDtoList.indexOf(dto);
                LocalAuthSetupSchema local = new LocalAuthSetupSchema();
                local.getRequestParams().get(0).setValueBasedOnInstance(dto.localAuthSetup.authenticationInfo);
                map.put(index, local);
            }
        }
        return map;
    }

    private static void setAuthEndpoint(EndpointSchema authEndpoint, JsonAuthRPCEndpointDto jsonAuthEndpoint) throws ClassNotFoundException{

        if (jsonAuthEndpoint.classNames != null && jsonAuthEndpoint.classNames.size() != jsonAuthEndpoint.jsonPayloads.size())
            throw new IllegalArgumentException("Driver Config Error: to specify inputs for auth endpoint, classNames and jsonPayloads should have same size");


        for (int i = 0; i < authEndpoint.getRequestParams().size(); i++){
            NamedTypedValue inputParam = authEndpoint.getRequestParams().get(i);
            String jsonString = jsonAuthEndpoint.jsonPayloads.get(i);

            if (jsonAuthEndpoint.classNames == null){
                setNamedValueBasedOnJsonString(inputParam,jsonString, i);
            }else{
                Class<?> clazz = Class.forName(jsonAuthEndpoint.classNames.get(i));

                try {
                    Object value = objectMapper.readValue(jsonString, clazz);
                    inputParam.setValueBasedOnInstance(value);
                } catch (JsonProcessingException e) {
                    SimpleLogger.recordErrorMessage("Driver Config Error: a jsonPayload at ("+i+") cannot be read as the object "+jsonAuthEndpoint.classNames.get(i));
                    setNamedValueBasedOnJsonString(inputParam,jsonString, i);
                }
            }

        }
    }

    private static void setNamedValueBasedOnJsonString(NamedTypedValue inputParam, String jsonString, int index){
        if (inputParam instanceof StringParam || inputParam instanceof PrimitiveOrWrapperParam || inputParam instanceof ByteBufferParam){
            setNamedValueBasedOnCandidates(inputParam, jsonString);
        } else if (inputParam instanceof ObjectParam){
            try {
                JsonNode node = objectMapper.readTree(jsonString);
                List<NamedTypedValue> fields = new ArrayList<>();
                for (NamedTypedValue f: ((ObjectParam) inputParam).getType().getFields()){
                    NamedTypedValue v = f.copyStructureWithProperties();
                    if (node.has(v.getName())){
                        setNamedValueBasedOnCandidates(f, node.textValue());
                        fields.add(v);
                    }else {
                        SimpleLogger.recordErrorMessage("Warning: cannot find field with the name "+v.getName()+" in the specified json");
                    }
                }
                inputParam.setValue(fields);
            } catch (JsonProcessingException ex) {
                SimpleLogger.recordErrorMessage("Driver Config Error: a jsonPayload at ("+index+") cannot be read as a JSON object with error:" +ex.getMessage());
            }
        }
    }



    private static List<AuthenticationDto> getAuthEndpointInInterface(List<AuthenticationDto> authenticationDtos, String interfaceName, Method method){
        if (authenticationDtos == null) return null;
        for (AuthenticationDto dto : authenticationDtos){
            if (dto.localAuthSetup == null && (dto.jsonAuthEndpoint == null || dto.jsonAuthEndpoint.endpointName == null || dto.jsonAuthEndpoint.interfaceName == null)){
                SimpleLogger.recordErrorMessage("Driver Config Error: To specify auth for RPC, either localAuthSetup or jsonAuthEndpoint should be specified." +
                        "For JsonAuthRPCEndpointDto, endpointName and interfaceName cannot be null");
            }
        }
        return authenticationDtos.stream().filter(a-> a.jsonAuthEndpoint != null
                && a.jsonAuthEndpoint.endpointName.equals(method.getName())
                && a.jsonAuthEndpoint.interfaceName.equals(interfaceName)).collect(Collectors.toList());
    }

    private static boolean filterRPCFunctionMethod(Method endpoint){


        /*
            filter streaming API

            Note that gRPC might exist streamAPI
            see https://grpc.io/docs/what-is-grpc/core-concepts/
            currently, we do not support such a stream yet in the communication

            examples of streaming APIs in gRPC
            https://github.com/grpc/grpc-java/blob/master/examples/src/main/java/io/grpc/examples/routeguide
            https://github.com/grpc/grpc-java/tree/master/examples/src/main/proto

         */
        if (isPotentialStreamingAPI(endpoint))
            return false;

        // only handle public method
        return Modifier.isPublic(endpoint.getModifiers());
    }

    private static boolean filterRPCFunctionMethodBasedOnSpecified(Method endpoint,
                                                                   List<String> skipEndpointsByName, List<String> skipEndpointsByAnnotation,
                                                                   List<String> involveEndpointsByName, List<String> involveEndpointsByAnnotation){
        if (skipEndpointsByName != null && involveEndpointsByName != null)
            throw new IllegalArgumentException("Driver Config Error: skipEndpointsByName and involveEndpointsByName should not be specified at same time.");
        if (skipEndpointsByAnnotation != null && involveEndpointsByAnnotation != null)
            throw new IllegalArgumentException("Driver Config Error: skipEndpointsByAnnotation and involveEndpointsByAnnotation should not be specified at same time.");

        if (skipEndpointsByName != null || skipEndpointsByAnnotation != null)
            return !anyMatchByNameAndAnnotation(endpoint, skipEndpointsByName, skipEndpointsByAnnotation);

        if (involveEndpointsByName != null || involveEndpointsByAnnotation != null)
            return anyMatchByNameAndAnnotation(endpoint, involveEndpointsByName, involveEndpointsByAnnotation);

        return true;
    }

    private static boolean isPotentialStreamingAPI(Method method){
        Class<?> returnType = method.getReturnType();
        if (returnType.equals(Iterator.class))
            return true;
        for (Parameter parameter : method.getParameters()){
           if (parameter.getType().equals(Iterator.class))
               return true;
        }
        return false;
    }

    private static boolean anyMatchByNameAndAnnotation(Method endpoint, List<String> names, List<String> annotations){
        boolean anyMatch = false;
        if (annotations != null){
            for (Annotation annotation : endpoint.getAnnotations()){
                anyMatch = anyMatch || annotations.contains(annotation.annotationType().getName());
            }
        }

        if (names != null)
            anyMatch = anyMatch || names.contains(endpoint.getName());

        return anyMatch;
    }


    private static String getClientClass(Object client){
        if (client == null) return null;
        String clazzType = client.getClass().getName();

        // handle com.sun.proxy
        if (!clazzType.startsWith("com.sun.proxy.")){
            return clazzType;
        }

        Class<?>[] clazz = client.getClass().getInterfaces();
        if (clazz.length == 0){
            SimpleLogger.recordErrorMessage("Error: the client is not related to any interface");
            return null;
        }

        if (clazz.length > 1)
            SimpleLogger.recordErrorMessage("ERROR: the client has more than one interfaces");

        return clazz[0].getName();

    }

    private static EndpointSchema build(InterfaceSchema schema, Method method, RPCType rpcType, List<AuthenticationDto> authenticationDtoList,
                                        List<CustomizedRequestValueDto> customizedRequestValueDtos,
                                        List<CustomizedNotNullAnnotationForRPCDto> notNullAnnotations) {
        List<NamedTypedValue> requestParams = new ArrayList<>();

        List<AuthenticationDto> authAnnotationDtos = getSpecificRelatedAuth(authenticationDtoList, method);
        List<Integer> authKeys = null;
        if (authAnnotationDtos != null)
            authKeys = authAnnotationDtos.stream().map(s-> authenticationDtoList.indexOf(s)).collect(Collectors.toList());

        Set<String> relatedCustomization = new HashSet<>();

        for (Parameter p : method.getParameters()) {
            requestParams.add(buildInputParameter(schema, p, rpcType, getRelatedCustomization(customizedRequestValueDtos, method), relatedCustomization, notNullAnnotations));
        }



        NamedTypedValue response = null;
        if (!method.getReturnType().equals(Void.TYPE)) {
            Map<TypeVariable, Type> genericTypeMap = new HashMap<>();
            response = build(schema, method.getReturnType(), method.getGenericReturnType(), "return", rpcType, new ArrayList<>(), 0, null, null, null, null, null, genericTypeMap, false);
        }

        List<NamedTypedValue> exceptions = null;
        if (method.getExceptionTypes().length > 0){
            exceptions = new ArrayList<>();
            for (int i = 0; i < method.getExceptionTypes().length; i++){
                NamedTypedValue exception = build(schema, method.getExceptionTypes()[i],
                        method.getGenericExceptionTypes()[i], "exception_"+i, rpcType, new ArrayList<>(), 0, null, null, null, null, null, null, false);
                exceptions.add(exception);
            }
        }

        return new EndpointSchema(method.getName(),
                schema.getName(), schema.getClientInfo(), requestParams, response, exceptions,
                authAnnotationDtos!= null && !authAnnotationDtos.isEmpty(), authKeys, relatedCustomization);
    }


    private static List<AuthenticationDto> getSpecificRelatedAuth(List<AuthenticationDto> authenticationDtoList, Method method){
        if (authenticationDtoList == null) return null;
        List<String> annotations = Arrays.stream(method.getAnnotations()).map(s-> s.annotationType().getName()).collect(Collectors.toList());
        return authenticationDtoList.stream().filter(s->
                (s.localAuthSetup != null && s.localAuthSetup.annotationOnEndpoint != null && annotations.contains(s.localAuthSetup.annotationOnEndpoint)) ||
                (s.jsonAuthEndpoint != null && s.jsonAuthEndpoint.annotationOnEndpoint != null && annotations.contains(s.jsonAuthEndpoint.annotationOnEndpoint))
        ).collect(Collectors.toList());
    }

    private static Map<Integer, CustomizedRequestValueDto> getRelatedCustomization(List<CustomizedRequestValueDto> customizedRequestValueDtos, Method method){
        if (customizedRequestValueDtos == null) return null;
        List<String> annotations = Arrays.stream(method.getAnnotations()).map(s-> s.annotationType().getName()).collect(Collectors.toList());
        List<CustomizedRequestValueDto> list = customizedRequestValueDtos.stream().filter(
                s-> (s.annotationOnEndpoint == null || annotations.contains(s.annotationOnEndpoint)) &&
                        (s.specificEndpointName == null || s.specificEndpointName.contains(method.getName()))
        ).collect(Collectors.toList());
        if (list.isEmpty()) return null;
        Map<Integer, CustomizedRequestValueDto> map = new HashMap<>();
        list.forEach(s->map.put(customizedRequestValueDtos.indexOf(s), s));
        return map;
    }

    private static NamedTypedValue buildInputParameter(InterfaceSchema schema, Parameter parameter, RPCType type,
                                                       Map<Integer,CustomizedRequestValueDto> customizationDtos, Set<String> relatedCustomization,
                                                       List<CustomizedNotNullAnnotationForRPCDto> notNullAnnotations) {
        String name = parameter.getName();
        Class<?> clazz = parameter.getType();
        List<String> flattenDepth = new ArrayList<>();
        int level = 0;
        Map<TypeVariable, Type> genericTypeMap = new HashMap<>();
        NamedTypedValue namedTypedValue = build(schema, clazz, parameter.getParameterizedType(), name, type, flattenDepth, level, customizationDtos, relatedCustomization, null, notNullAnnotations, null, genericTypeMap, false);

        for (Annotation annotation: parameter.getAnnotations()){
            handleConstraint(namedTypedValue, annotation, notNullAnnotations);
        }
        return namedTypedValue;
    }

    private static NamedTypedValue build(InterfaceSchema schema, Class<?> clazz, Type genericType, String name, RPCType rpcType,
                                         List<String> flattenDepth, int level, Map<Integer, CustomizedRequestValueDto> customizationDtos,
                                         Set<String> relatedCustomization, AccessibleSchema accessibleSchema,
                                         List<CustomizedNotNullAnnotationForRPCDto> notNullAnnotations, Class<?> originalType,
                                         Map<TypeVariable, Type> genericTypeMap, boolean isTypeToIdentify) {
        handleGenericSuperclass(clazz, genericTypeMap);
        List<String> genericTypes = handleGenericType(clazz, genericType, genericTypeMap);
        String clazzWithGenericTypes = CodeJavaOrKotlinGenerator.handleClassNameWithGeneric(clazz.getName(), genericTypes);
        flattenDepth.add(getObjectTypeNameWithFlag(clazz, clazzWithGenericTypes, level));
        NamedTypedValue namedValue = null;

        JavaDtoSpec spec = JavaDtoSpec.DEFAULT;
        if (rpcType == RPCType.gRPC || isProtobuf(clazz))
            spec = JavaDtoSpec.PROTO3;


        try{

            if (PrimitiveOrWrapperType.isPrimitiveOrTypes(clazz)) {
                namedValue = PrimitiveOrWrapperParam.build(name, clazz, accessibleSchema, spec);
            } else if (clazz == String.class) {
                StringType stringType = new StringType(spec);
                namedValue = new StringParam(name, stringType, accessibleSchema);
            } else if (clazz == BigDecimal.class){
                BigDecimalType bigDecimalType = new BigDecimalType(spec);
                namedValue = new BigDecimalParam(name, bigDecimalType, accessibleSchema);
            } else if (clazz == BigInteger.class){
                BigIntegerType bigIntegerType = new BigIntegerType(spec);
                namedValue = new BigIntegerParam(name, bigIntegerType, accessibleSchema);
            } else if (clazz.isEnum()) {
                String [] items = Arrays.stream(clazz.getEnumConstants()).map(e-> getNameEnumConstant(e)).toArray(String[]::new);
                EnumType enumType = new EnumType(clazz.getSimpleName(), clazz.getName(), items, clazz, spec);
                EnumParam param = new EnumParam(name, enumType, accessibleSchema);
                //register this type in the schema
                schema.registerType(enumType.copy(), param.copyStructureWithProperties(), isTypeToIdentify);
                namedValue = param;
            } else if (clazz.isArray()){

                Type type = null;
                Class<?> templateClazz = null;
                if (genericType instanceof GenericArrayType){
                    type = ((GenericArrayType)genericType).getGenericComponentType();
                    templateClazz = getTemplateClass(type, genericTypeMap);
                }else {
                    templateClazz = clazz.getComponentType();
                }

                NamedTypedValue template = build(schema, templateClazz, type,"template", rpcType, flattenDepth, level, customizationDtos, relatedCustomization, null, notNullAnnotations, null, genericTypeMap, isTypeToIdentify);
                template.setNullable(false);
                CollectionType ctype = new CollectionType(clazz.getSimpleName(),clazz.getName(), template, clazz, spec);
                ctype.depth = getDepthLevel(clazz, flattenDepth, level, clazzWithGenericTypes);
                namedValue = new ArrayParam(name, ctype, accessibleSchema);

            } else if (clazz == ByteBuffer.class){
                // handle binary of thrift
                namedValue = new ByteBufferParam(name, accessibleSchema, spec);
            } else if (clazz.getName().equals(Protobuf3ByteStringType.PROTOBUF3_BYTE_STRING_TYPE_NAME)){
                Protobuf3ByteStringType type = Protobuf3ByteStringType.getInstance(spec, clazz);
                namedValue = new Protobuf3ByteStringParam(name, type, accessibleSchema);
            } else if (List.class.isAssignableFrom(clazz) || Set.class.isAssignableFrom(clazz)){
//                if (genericType == null)
//                    throw new RuntimeException("genericType should not be null for List and Set class");

                NamedTypedValue template = null;
                if (genericType != null){
                    Type type = ((ParameterizedType) genericType).getActualTypeArguments()[0];
                    Class<?> templateClazz = getTemplateClass(type, genericTypeMap);
                    template = build(schema, templateClazz, type,"template", rpcType, flattenDepth, level, customizationDtos, relatedCustomization, null, notNullAnnotations, null, genericTypeMap, isTypeToIdentify);
                }else {
                    // if the generic type is not specified, use String as default for the moment
                    template = new StringParam(name, new StringType(spec), null);
                }

                template.setNullable(false);
                CollectionType ctype = new CollectionType(clazz.getSimpleName(),clazz.getName(), template, clazz, spec);
                ctype.depth = getDepthLevel(clazz, flattenDepth, level, clazzWithGenericTypes);
                if (List.class.isAssignableFrom(clazz))
                    namedValue = new ListParam(name, ctype, accessibleSchema);
                else if(Set.class.isAssignableFrom(clazz))
                    namedValue = new SetParam(name, ctype, accessibleSchema);
            } else if (Map.class.isAssignableFrom(clazz)){
                if (genericType == null)
                    throw new RuntimeException("genericType should not be null for List and Set class");
                Type keyType = ((ParameterizedType) genericType).getActualTypeArguments()[0];
                Type valueType = ((ParameterizedType) genericType).getActualTypeArguments()[1];

                Class<?> keyTemplateClazz = getTemplateClass(keyType, genericTypeMap);
                NamedTypedValue keyTemplate = build(schema, keyTemplateClazz, keyType,"keyTemplate", rpcType, flattenDepth, level, customizationDtos, relatedCustomization, null, notNullAnnotations, null, genericTypeMap, isTypeToIdentify);
                keyTemplate.setNullable(false);

                Class<?> valueTemplateClazz = getTemplateClass(valueType, genericTypeMap);
                NamedTypedValue valueTemplate = build(schema, valueTemplateClazz, valueType,"valueTemplate", rpcType, flattenDepth, level,customizationDtos, relatedCustomization, null, notNullAnnotations, null, genericTypeMap, isTypeToIdentify);
                MapType mtype = new MapType(clazz.getSimpleName(), clazz.getName(), new PairParam(new PairType(keyTemplate, valueTemplate,spec), null), clazz,spec);
                mtype.depth = getDepthLevel(clazz, flattenDepth, level, clazzWithGenericTypes);
                namedValue = new MapParam(name, mtype, accessibleSchema);
            } else if (Date.class.isAssignableFrom(clazz)){
                if (clazz == Date.class)
                    namedValue = new DateParam(name, new UtilDateType(spec), accessibleSchema);
                else
                    throw new RuntimeException("NOT support "+clazz.getName()+" date type in java yet");
            } else if (LocalDate.class.isAssignableFrom(clazz)){
                if (clazz == LocalDate.class)
                    namedValue = new DateParam(name, new LocalDateType(spec), accessibleSchema);
                else
                    throw new RuntimeException("NOT support "+clazz.getName()+" date type in java yet");

            } else if (Exception.class.isAssignableFrom(clazz) && clazz.getName().startsWith("java")){
                // note that here we only extract class name and message
                StringParam msgField = new StringParam("message", new AccessibleSchema(false, null, "getMessage", String.class), spec);
                ObjectType exceptionType = new ObjectType(clazz.getSimpleName(), clazz.getName(), Collections.singletonList(msgField), clazz, genericTypes,spec );
                namedValue = new ObjectParam(name, exceptionType, accessibleSchema);
            } else {
                if (clazz.getName().startsWith("java")){
                    throw new RuntimeException("NOT handle "+clazz.getName()+" class in java yet");
                }

                long cycleSize = getCycleSize(flattenDepth,clazz,clazzWithGenericTypes,level);

                if (cycleSize == 1){
                    List<NamedTypedValue> fields = new ArrayList<>();

                    Map<Integer, CustomizedRequestValueDto> objRelatedCustomizationDtos = getCustomizationBasedOnSpecifiedType(customizationDtos, clazz.getName());

                    int flevel = level + 1;

                    if (rpcType == RPCType.gRPC || isProtobuf(clazz)){
                        List<Protobuf3Field> pfList = getProtobuf3FieldsAndType(clazz);
                        for (Protobuf3Field pf : pfList){
                            AccessibleSchema faccessSchema = new AccessibleSchema(false, pf.setterName, pf.getterName, pf.fieldType, pf.setterInputParams);

                            NamedTypedValue field = build(schema, pf.fieldType, pf.genericType, pf.fieldName, rpcType, flattenDepth, flevel, objRelatedCustomizationDtos, relatedCustomization, faccessSchema, notNullAnnotations, null, genericTypeMap, isTypeToIdentify);

                            fields.add(field);
                        }

                    }else{
                        // field list
                        List<Field> fieldList = new ArrayList<>();
                        getAllFields(clazz, fieldList, rpcType);

                        for(Field f: fieldList){
                            // skip final field
                            if (Modifier.isFinal(f.getModifiers()))
                                continue;

                            if (doSkipReflection(f.getName()))
                                continue;

                            // always try to find the setter and getter
                            AccessibleSchema faccessSchema = extractAccessibleSchema(clazz, f);
                            //check accessible
                            if (!Modifier.isPublic(f.getModifiers())){
                                if (faccessSchema.getterMethodName == null || faccessSchema.setterMethodName == null){
                                    SimpleLogger.recordErrorMessage("Error: skip the field "+f.getName()+" since its setter/getter is not found");
                                    continue;
                                }
                            }

                            Class<?> fType = f.getType();
                            Class<?> foriginalType = null;
                            Type fGType = f.getGenericType();

                            if (f.getGenericType() instanceof TypeVariable){
                                foriginalType = f.getType();
                                Type actualType = getActualType(genericTypeMap, (TypeVariable) f.getGenericType());
                                if (actualType instanceof Class){
                                    fType = (Class<?>) actualType;
                                    fGType = fType;
                                }else if (actualType instanceof ParameterizedType){
                                    fGType = actualType;
                                    if (((ParameterizedType) actualType).getRawType() instanceof Class<?>)
                                        fType = (Class<?>) ((ParameterizedType) actualType).getRawType();
                                    else
                                        throw new RuntimeException("Error: Fail to handle actual type of a generic type");
                                }
                            }

                            NamedTypedValue field = build(schema, fType, fGType,f.getName(), rpcType, flattenDepth, flevel, objRelatedCustomizationDtos, relatedCustomization, faccessSchema, notNullAnnotations, foriginalType, genericTypeMap, isTypeToIdentify);
                            for (Annotation annotation : f.getAnnotations()){
                                handleConstraint(field, annotation, notNullAnnotations);
                            }
                            fields.add(field);
                        }
                    }



                    handleNativeRPCConstraints(clazz, fields, rpcType);

                    ObjectType otype = new ObjectType(clazz.getSimpleName(), clazz.getName(), fields, clazz, genericTypes, spec);
                    otype.setOriginalType(originalType);
                    otype.depth = getDepthLevel(clazz, flattenDepth, level, clazzWithGenericTypes);
                    ObjectParam oparam = new ObjectParam(name, otype, accessibleSchema);
                    schema.registerType(otype.copy(), oparam, isTypeToIdentify);
                    namedValue = oparam;
                }else {
                    CycleObjectType otype = new CycleObjectType(clazz.getSimpleName(), clazz.getName(), clazz, genericTypes, spec);
                    otype.depth = getDepthLevel(clazz, flattenDepth, level,clazzWithGenericTypes);
                    ObjectParam oparam = new ObjectParam(name, otype, accessibleSchema);
                    schema.registerType(otype.copy(), oparam, isTypeToIdentify);
                    namedValue = oparam;
                }
            }
        }catch (ClassCastException e){
            throw new RuntimeException(String.format("fail to perform reflection on param/field: %s; class: %s; genericType: %s; class of genericType: %s; depth: %s; error info:%s",
                    name, clazz.getName(), genericType==null?"null":genericType.getTypeName(), genericType==null?"null":genericType.getClass().getName(), String.join(",", flattenDepth), e.getMessage()));
        }

        namedValue.getType().setOriginalType(originalType);

        if (customizationDtos!=null){
            handleNamedValueWithCustomizedDto(namedValue, customizationDtos, relatedCustomization);
        }

        return namedValue;
    }

    private static int getCycleSize(List<String> flattenDepth,Class<?> clazz, String name, int level){
        int cycle = 1;
        for (String obj : flattenDepth){
            String[] info = parseObjectTypeFlag(obj);
            if (!isNotCustomizedObject(clazz) && info != null && Integer.parseInt(info[2]) < level && info[1].equals(name)){
                cycle++;
            }
        }
        return cycle;
    }

    private static boolean isProtobuf(Class<?> clazz){
        if (clazz == null) return false;
        boolean isProtobuf= clazz.getName().startsWith(PROTOBUF_PACKAGE);
        if (isProtobuf) return isProtobuf;

        Class pclazz = clazz.getSuperclass();
        if (pclazz != null){
            return isProtobuf(pclazz);
        }

        return false;
    }

    private static List<Protobuf3Field> getProtobuf3FieldsAndType(Class<?> clazz){
        Optional<Class<?>> op = Arrays.stream(clazz.getDeclaredClasses()).filter(s-> s.getSimpleName().equals(PROTOBUF_BUILDER)).findFirst();
        if (!op.isPresent()) return null;

        List<Protobuf3Field> list = new ArrayList<>();

        for (Field f : op.get().getDeclaredFields()){
            if (filterProtobuf3Field(f)){
                String fieldName = formatProtobuf3FieldName(f.getName());
                Protobuf3Field pf = findProtobuf3FieldType(op.get(), fieldName);
                if (pf != null)
                    list.add(pf);
            }
        }
        return list;
    }

    private static String getNameEnumConstant(Object object) {
        try {
            Method name = object.getClass().getMethod("name");
            name.setAccessible(true);
            return (String) name.invoke(object);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            SimpleLogger.recordErrorMessage("Driver Error: fail to extract name for enum constant:"+ e.getMessage());
            return object.toString();
        }
    }

    private static void handleGenericSuperclass(Class clazz, Map<TypeVariable, Type> map){
        if (isNotCustomizedObject(clazz)) return;
        if (clazz.getGenericSuperclass() == null || !(clazz.getGenericSuperclass() instanceof ParameterizedType)) return;
        Type[] actualTypes = ((ParameterizedType) clazz.getGenericSuperclass()).getActualTypeArguments();
        if (((ParameterizedType) clazz.getGenericSuperclass()).getActualTypeArguments().length == 0) return;
        TypeVariable[] typeVariables = clazz.getSuperclass().getTypeParameters();
        if (typeVariables.length != actualTypes.length){
            throw new RuntimeException("Error: fail to handle generic types in Dto");
        }
        for (int i = 0; i < typeVariables.length; i++){
            map.put(typeVariables[i], actualTypes[i]);
        }
        handleGenericSuperclass(clazz.getSuperclass(), map);
    }

    private static List<String> handleGenericType(Class<?> clazz, Type genericType, Map<TypeVariable, Type> map){
        if (isNotCustomizedObject(clazz)) return null;
        if (!(genericType instanceof ParameterizedType)) return null;
        List<String> genericTypes = new ArrayList<>();
        Type[] actualTypes = ((ParameterizedType) genericType).getActualTypeArguments();
        TypeVariable[] typeVariables = clazz.getTypeParameters();
        if (typeVariables.length != actualTypes.length){
            throw new RuntimeException("Error: fail to handle generic types in Dto");
        }
        for (int i = 0; i < typeVariables.length; i++){
            Type a = actualTypes[i];
            if (a instanceof TypeVariable)
                a = getActualType(map, (TypeVariable) a);
            if (a != null)
                genericTypes.add(a.getTypeName());

            map.put(typeVariables[i], actualTypes[i]);
        }
        return genericTypes;
    }

    private static Type getActualType(Map<TypeVariable, Type> map, TypeVariable typeVariable){
        Type t = map.get(typeVariable);
        if (t == null) return null;
        if (t instanceof TypeVariable)
            return getActualType(map, (TypeVariable) t);
        return t;
    }

    private static void getAllFields(Class<?> clazz, List<Field> fieldList, RPCType type){
        if (isNativeThriftDto(clazz)){
            getFieldForNativeThriftDto(clazz, fieldList);
            return;
        }

        fieldList.addAll(0, Arrays.asList(clazz.getDeclaredFields()));
        if (!Exception.class.isAssignableFrom(clazz) && clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class)
            getAllFields(clazz.getSuperclass(), fieldList, type);
    }

    private static Map<Integer, CustomizedRequestValueDto> getCustomizationBasedOnSpecifiedType(Map<Integer, CustomizedRequestValueDto> customizationDtos, String objTypeName){
        if (customizationDtos == null) return null;
        return customizationDtos.entrySet().stream().filter(s-> s.getValue().specificRequestTypeName == null ||
                s.getValue().specificRequestTypeName.equals(objTypeName)).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static AccessibleSchema extractAccessibleSchema(Class<?> clazz, Field field){
//        if (Modifier.isPublic(field.getModifiers()))
//            return new AccessibleSchema();

        Method getter = Arrays.stream(clazz.getMethods()).filter(m->
                Modifier.isPublic(m.getModifiers()) && isGetter(field.getName(), m.getName(), field.getType().getTypeName()) && m.getParameterCount() == 0
        ).findFirst().orElse(null);


        Method setter = Arrays.stream(clazz.getMethods()).filter(m->
                Modifier.isPublic(m.getModifiers()) &&
                    isSetter(field.getName(), m.getName(), field.getType().getTypeName()) &&
                    m.getParameterCount() == 1 &&
                    (m.getParameterTypes()[0].equals(field.getType()) || m.getParameterTypes()[0].equals(PrimitiveOrWrapperParam.getPrimitiveOrWrapper(field.getType())))
        ).findFirst().orElse(null);

        return new AccessibleSchema(Modifier.isPublic(field.getModifiers()), (setter != null)?setter.getName():null, (getter != null)?getter.getName():null, (getter != null)?getter.getReturnType():null);

    }

    private static String findGetterOrSetter(Class<?> clazz, Field field, boolean findGetter){
        List<Method> found;
        if (findGetter){
            found = Arrays.stream(clazz.getMethods()).filter(m->
                    Modifier.isPublic(m.getModifiers()) &&
//                            (m.getName().equalsIgnoreCase("get"+field.getName()) || m.getName().equalsIgnoreCase("is"+field.getName())) &&
                            isGetter(field.getName(), m.getName(), field.getType().getTypeName()) &&
                            m.getParameterCount() == 0
            ).collect(Collectors.toList());
        }else {
            found = Arrays.stream(clazz.getMethods()).filter(m->
                    Modifier.isPublic(m.getModifiers()) &&
//                            m.getName().equalsIgnoreCase("set"+field.getName()) &&
                            isSetter(field.getName(), m.getName(), field.getType().getTypeName()) &&
                            m.getParameterCount() == 1 &&
                            (m.getParameterTypes()[0].equals(field.getType()) || m.getParameterTypes()[0].equals(PrimitiveOrWrapperParam.getPrimitiveOrWrapper(field.getType())))
            ).collect(Collectors.toList());
        }
        if (found.size() == 1)
            return found.get(0).getName();

        // no need to get setter or getter
        if (Modifier.isPublic(field.getModifiers()))
            return null;

        String msg = "RPC extract schema Error: cannot access field property, there exist "+found.size()+" methods to access the field "+ field.getName() + " for the class "+ clazz.getName();

        if (found.size() > 1){
            /*
                instead of throwing the exception,
                provide a warning and use the first one
             */
            SimpleLogger.recordErrorMessage(msg);
            return found.get(0).getName();
        }

        SimpleLogger.recordErrorMessage(msg);
        return null;

    }

    private static boolean isSetter(String fieldName, String methodName, String type){
        boolean isBoolean = type.equals(Boolean.class.getName()) || type.equals(boolean.class.getName());
        String fieldText = fieldName;
        if (isBoolean && fieldText.startsWith("is") && fieldText.length() > 2)
            fieldText = fieldText.substring(2);
        String gsMethod = "set";
        return methodName.equalsIgnoreCase(gsMethod+fieldText) || methodName.equalsIgnoreCase(gsMethod+fieldName);
    }

    private static boolean isGetter(String fieldName, String methodName, String type){
        boolean isBoolean = type.equals(Boolean.class.getName()) || type.equals(boolean.class.getName());
        return methodName.equalsIgnoreCase("get"+fieldName)
                || (isBoolean && (methodName.equalsIgnoreCase(fieldName)
                || methodName.equalsIgnoreCase("is"+fieldName)))
                || (isBoolean && fieldName.startsWith("is") && methodName.equalsIgnoreCase(fieldName.replaceFirst("is", "get")));
    }

    private static String formatProtobuf3FieldName(String fieldName){
        if (fieldName.endsWith("_"))
            return fieldName.substring(0, fieldName.length()-1);
        return fieldName;
    }

    private static Protobuf3Field findProtobuf3FieldType(Class<?> clazz, String fieldName){
        Method setter = null;
        Method getter = null;

        Class<?> getterClazz = clazz;
        /*
            parse field getter from interface
         */
        if (clazz.getInterfaces().length == 1 && clazz.getInterfaces()[0].getName().endsWith(PROTOBUF_INTERFACE_BUILDER_SUFFIX)){
            getterClazz = clazz.getInterfaces()[0];
        }


        List<Method> getters = Arrays.stream(getterClazz.getDeclaredMethods())
            .filter(
                m-> m.getParameters().length == 0
                    && (m.getAnnotation(java.lang.Deprecated.class) == null)
                    && (m.getName().equalsIgnoreCase("get" + fieldName)
                        || m.getName().equalsIgnoreCase("get" + fieldName + PROTOBUF_LIST_FIELD_SUFFIX)
                        || m.getName().equalsIgnoreCase("get" + fieldName + PROTOBUF_MAP_FIELD_SUFFIX)
                    )
            ).collect(Collectors.toList());
//        if (getters.size() == 2 && getters.stream().anyMatch(s-> List.class.isAssignableFrom(s.getReturnType())))
//            getters = getters.stream().filter(m -> (!m.getReturnType().getName().startsWith(PROTOBUF_PACKAGE))).collect(Collectors.toList());

        if (getters.size() != 1) return null;

        getter = getters.get(0);

        if (getter != null && filterProtobuf3Type(getter.getReturnType())){
            String setterName = "set"+fieldName;
            if (Map.class.isAssignableFrom(getter.getReturnType())){
                setterName = PROTOBUF_MAP_SETTER_PREFIX + fieldName;
            }else if (List.class.isAssignableFrom(getter.getReturnType())){
                setterName = PROTOBUF_LIST_SETTER_PREFIX + fieldName;
            }
            for (Method m : clazz.getDeclaredMethods()){
                if (m.getName().equalsIgnoreCase(setterName)
                    && m.getParameterTypes().length == 1
                    && m.getParameterTypes()[0].isAssignableFrom(getter.getReturnType())){
                    setter = m;
                    break;
                }
            }
        }

        if (getter!=null && setter != null) {
            Protobuf3Field pf = new Protobuf3Field();
            pf.fieldName = fieldName;
            pf.fieldType = getter.getReturnType();
            pf.genericType = getter.getGenericReturnType();
            pf.getterName = getter.getName();
            pf.setterName = setter.getName();
            pf.setterInputParams = setter.getParameterTypes();
            return pf;
        }
        return null;
    }

    private static boolean filterProtobuf3Field(Field field){
        return (!field.getName().equals("bitField0_"));
    }


    private static boolean filterProtobuf3Type(Class<?> clazz){
        /*
            support all types for proto 3
            might add if we found any type is not supported yet
         */
        return true;
    }

    private static void handleNamedValueWithCustomizedDto(NamedTypedValue namedTypedValue, Map<Integer, CustomizedRequestValueDto> customizationDtos, Set<String> relatedCustomization){

        List<String> candidateReferences = new ArrayList<>();
        List<NamedTypedValue> candidates = new ArrayList<>();
        customizationDtos.forEach((i, dto)->{
            if (dto.combinedKeyValuePairs != null
                   // && (dto.specificRequestTypeName == null || dto.specificRequestTypeName.equals(namedTypedValue.getType().getFullTypeName()))
            ){
                dto.combinedKeyValuePairs.forEach(p->{
                    if (p.fieldKey.equals(namedTypedValue.getName())){
                        NamedTypedValue copy = namedTypedValue.copyStructureWithProperties();
                        boolean ok = setNamedValueBasedOnCandidates(copy, p.fieldValue);
                        if (ok){
                            if (!candidateReferences.contains(""+i)){
                                relatedCustomization.add(""+i);
                                candidateReferences.add(""+i);
                                candidates.add(copy);
                            } else
                                throw new IllegalArgumentException("Error: there should not exist same key with the name "+p.fieldKey+"in a combinedKeyValuePairs");
                        }
                    }
                });
            }
        });

        if (!candidates.isEmpty()){
            namedTypedValue.setCandidateReferences(candidateReferences);
            namedTypedValue.setCandidates(candidates);
            return;
        }

        // check for keyValues
        List<CustomizedRequestValueDto> ikey = customizationDtos.values().stream().filter(s-> s.keyValues!= null && s.keyValues.key.equals(namedTypedValue.getName())
                //&& (s.specificRequestTypeName== null || s.specificRequestTypeName.equals(namedTypedValue.getType().getFullTypeName()))
        ).collect(Collectors.toList());
        if (ikey.size() == 1){
            setCandidatesForNamedValue(namedTypedValue, ikey.get(0));
        } else if (ikey.size() > 1){
            throw new IllegalStateException("Error: more than one Dto for independent key with "+getKeyForCustomizedRequestValueDto(ikey.get(0)));
        }
    }

    private static void setCandidatesForNamedValue(NamedTypedValue namedTypedValue, CustomizedRequestValueDto customizedRequestValueDto){
        boolean handled = true;
        List<NamedTypedValue> candidates = new ArrayList<>();
        if (namedTypedValue instanceof PrimitiveOrWrapperParam || namedTypedValue instanceof StringParam || namedTypedValue instanceof ByteBufferParam){

            for (String v: customizedRequestValueDto.keyValues.values){
                NamedTypedValue copy= namedTypedValue.copyStructureWithProperties();
                handled = handled && setNamedValueBasedOnCandidates(copy, v);
                candidates.add(copy);
            }
        }else {
            SimpleLogger.recordErrorMessage("Error: Do not support configuring pre-defined values for the type "+namedTypedValue.getType().getFullTypeName());
            return;
        }

        if (handled){
            namedTypedValue.setCandidates(candidates);
        }
    }

    private static boolean setNamedValueBasedOnCandidates(NamedTypedValue copy, String value){
        try {
            if (copy instanceof PrimitiveOrWrapperParam){
                ((PrimitiveOrWrapperParam) copy).setValueBasedOnStringValue(value);
            }else if (copy instanceof StringParam)
                copy.setValue(value);
            else if (copy instanceof ByteBufferParam)
                copy.setValue(value.getBytes());


        }catch (RuntimeException exception){
            SimpleLogger.recordErrorMessage("Error: fail to generate candidates with string value "+value+" for "+copy.getName() +" with type "+copy.getType().getFullTypeName());
            return false;
        }
        return true;
    }

    private static void handleConstraint(NamedTypedValue namedTypedValue, Annotation annotation, List<CustomizedNotNullAnnotationForRPCDto> notNullAnnotations){
        if (annotation.annotationType().getName().startsWith("javax.validation.constraints")){
            JavaXConstraintHandler.handleParam(namedTypedValue, annotation);
        } else if (notNullAnnotations != null && !notNullAnnotations.isEmpty()){
            boolean isRequired = notNullAnnotations.stream().anyMatch(a-> isRequired(annotation, a));
            namedTypedValue.setNullable(!isRequired);
        }
        // remove the log for the moment, might need it later
//        else {
//            SimpleLogger.info("annotation with "+ annotation.annotationType().getName()+" is not handled");
//        }
    }

    private static boolean isRequired(Annotation annotation, CustomizedNotNullAnnotationForRPCDto notNullAnnotations){
        if (annotation.annotationType().getName().equals(notNullAnnotations.annotationType)){
            if (notNullAnnotations.annotationMethod != null && notNullAnnotations.equalsTo !=null){
                try {
                    return annotation.annotationType().getDeclaredMethod(notNullAnnotations.annotationMethod).invoke(annotation).equals(notNullAnnotations.equalsTo);
                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    SimpleLogger.recordErrorMessage("Error: fail to invoke the specified method in the annotation with the error msg:"+e.getMessage());
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static Class<?> getTemplateClass(Type type, Map<TypeVariable, Type> genericTypeMap){
        Type actualType = type;
        if (type instanceof TypeVariable)
            actualType = getActualType(genericTypeMap, (TypeVariable) type);

        if (actualType instanceof ParameterizedType){
            return  (Class<?>) ((ParameterizedType)actualType).getRawType();
        }else if (actualType instanceof Class)
            return  (Class<?>) actualType;
        throw new RuntimeException("unhanded type:"+ type);
    }

    /**
     * there might exist some additional info generated by eg instrumentation
     * then we need to skip reflection on such info with the specified name
     * @param name is a name of object to check
     * @return whether to skip the object
     */
    private static boolean doSkipReflection(String name){
        return name.equals("$jacocoData");
    }

    private static boolean isMetaMap(Field field){
        boolean result = field.getName().equals("metaDataMap")
                && Map.class.isAssignableFrom(field.getType());
        if (!result) return result;
        Type genericType = field.getGenericType();

        Type valueType = ((ParameterizedType) genericType).getActualTypeArguments()[1];

        return valueType.getTypeName().equals("org.apache.thrift.meta_data.FieldMetaData");

    }

    private final static String NATIVE_THRIFT_DTO_INTERFACE = "org.apache.thrift.TBase";
    private final static String NATIVE_THRIFT_FIELD_SCHEMA = "metaDataMap";

    private static boolean isNativeThriftDto(Class<?> clazz){
        return clazz.getInterfaces().length > 0 && Arrays.stream(clazz.getInterfaces()).anyMatch(i-> i.getName().equals(NATIVE_THRIFT_DTO_INTERFACE));
    }

    private static void getFieldForNativeThriftDto(Class<?> clazz, List<Field> fields){
        try {
            Field metaMap_field = clazz.getDeclaredField(NATIVE_THRIFT_FIELD_SCHEMA);
            if (isMetaMap(metaMap_field)){
                Object metaMap = metaMap_field.get(null);

                if (metaMap instanceof Map){
                    for (Object f : ((Map)metaMap).values()){
                        Field fname = f.getClass().getDeclaredField("fieldName");
                        fname.setAccessible(true);
                        String name = (String) fname.get(f);
                        fields.add(clazz.getDeclaredField(name));
                    }
                }
            }

        } catch (NoSuchFieldException | IllegalAccessException e) {
            SimpleLogger.recordErrorMessage("Error: fail to get the metaDataMap field in native dto");
        }

    }

    private static void handleNativeRPCConstraints(Class<?> clazz, List<NamedTypedValue> fields, RPCType type){
        if (isNativeThriftDto(clazz)){
            try {
                Field metaMap_field = clazz.getDeclaredField(NATIVE_THRIFT_FIELD_SCHEMA);
                if (isMetaMap(metaMap_field))
                    handleMetaMap(metaMap_field, fields);
            } catch (NoSuchFieldException e) {
                SimpleLogger.recordErrorMessage("Error: fail to get the metaDataMap field in native dto");
            }
        }

    }

    private static void handleMetaMap(Field metaMap_field, List<NamedTypedValue> fields){
        Object metaMap = null;
        try {
            metaMap = metaMap_field.get(null);

            if (metaMap instanceof Map){
                for (Object f : ((Map)metaMap).values()){
                    Field fname = f.getClass().getDeclaredField("fieldName");
                    fname.setAccessible(true);
                    String name = (String) fname.get(f);
                    NamedTypedValue field = findFieldByName(name, fields);
                    if (field!=null){
                        Field frequiredType = f.getClass().getDeclaredField("requirementType");
                        frequiredType.setAccessible(true);
                        byte required = (byte)frequiredType.get(f);
                        if (required == 1)
                            field.setNullable(false);
                        // TODO for handling default
                    }else {
                        SimpleLogger.recordErrorMessage("Error: fail to find field in list but exist in metaMap, and the field name is "+ name);
                    }
                }
            }
        } catch (IllegalAccessException | NoSuchFieldException e) {
            SimpleLogger.recordErrorMessage("Error: fail to set isNull based on metaMap of Thrift struct "+e.getMessage());
        }
    }

    private static NamedTypedValue findFieldByName(String name, List<NamedTypedValue> fields){
        for (NamedTypedValue f: fields){
            if (f.getName().equals(name)) return f;
        }
        return null;
    }

    private static int getDepthLevel(Class clazz, List<String> flattendepth, int level, String clazzFullNameWithGeneric){
        String tag = getObjectTypeNameWithFlag(clazz, clazzFullNameWithGeneric, -2);
        int start = 0;
        for (int i =0; i < flattendepth.size(); i++){
            if (flattendepth.get(i).startsWith(tag))
                start = i;
        }
        return flattendepth.subList(start, flattendepth.size()).stream().filter(s-> !s.startsWith(tag) && s.startsWith(OBJECT_FLAG)).collect(Collectors.toSet()).size();
    }

    public static Map<String, List<RPCActionDto>> buildSeededTest(Map<String, InterfaceSchema> rpcInterfaceSchema, List<SeededRPCTestDto> seedRPCTests, RPCType rpcType){
        Map<String, List<RPCActionDto>> results = new HashMap<>();


        for (SeededRPCTestDto dto: seedRPCTests){
            if (dto.rpcFunctions != null && !dto.rpcFunctions.isEmpty()){
                List<RPCActionDto> test = new ArrayList<>();
                try{
                    for (SeededRPCActionDto actionDto : dto.rpcFunctions){
                        InterfaceSchema schema = rpcInterfaceSchema.get(actionDto.interfaceName);
                        if (schema != null){
                            EndpointSchema actionSchema = schema.getOneEndpointWithSeededDto(actionDto);
                            if (actionSchema != null){
                                EndpointSchema copy = actionSchema.copyStructure();
                                for (int i = 0; i < copy.getRequestParams().size(); i++){
                                    // TODO need to check if generic type could be handled with jackson
                                    NamedTypedValue p = copy.getRequestParams().get(i);
                                    try {
                                        String stringValue = actionDto.inputParams.get(i);
                                        // Object value = objectMapper.readValue(stringValue, p.getType().getClazz());
                                        p.setValueBasedOnInstanceOrJson(stringValue);

                                    } catch (JsonProcessingException e) {
                                        SimpleLogger.recordErrorMessage(
                                                String.format("Seeded Test Error: cannot parse the seeded test %s at the parameter %d with error msg: %s", actionDto, i, e.getMessage()));
                                    }
                                }
                                RPCActionDto rpcActionDto = copy.getDto();
                                rpcActionDto.mockRPCExternalServiceDtos = actionDto.mockRPCExternalServiceDtos;
                                rpcActionDto.mockDatabaseDtos = actionDto.mockDatabaseDtos;
                                handleExternalResponses(schema, actionDto, rpcType);
                                test.add(rpcActionDto);
                            }else {
                                SimpleLogger.recordErrorMessage("Seeded Test Error: cannot find the action "+actionDto.functionName);
                            }
                        } else {
                            SimpleLogger.recordErrorMessage("Seeded Test Error: cannot find the interface "+ actionDto.interfaceName);
                        }
                    }
                    results.put( String.format("%s_INDEX_%d", (dto.testName != null)?dto.testName:"untitled", seedRPCTests.indexOf(dto)), test);
                }catch (RuntimeException e){
                    SimpleLogger.recordErrorMessage("Fail to handle specified seeded test: "+ ((dto.testName != null)? dto.testName:"index_"+seedRPCTests.indexOf(dto)));
                    StringBuilder msg = extractExceptionMsg(e);
                    SimpleLogger.recordErrorMessage(msg.toString());
                }
            } else {
                SimpleLogger.warn("Seeded Test: empty RPC function calls for the test "+ ((dto.testName != null)? dto.testName:"index_"+seedRPCTests.indexOf(dto)));
            }
        }
        return results;
    }

    private static StringBuilder extractExceptionMsg(RuntimeException e) {
        StringBuilder msg = new StringBuilder("Fail to handle specified seeded test with exception ");
        msg.append(e.getClass().getName()).append(" with message:").append(e.getMessage()).append(".");
        // reduce multiple copy of the stack
        StackTraceElement[] exceptionStack = e.getStackTrace();
        if (exceptionStack != null && exceptionStack.length > 0){
            msg.append(" with stack:");
            for (int i = 0; i < Math.min(exceptionStack.length, 5); i++){
                msg.append(exceptionStack[i].toString());
                msg.append(System.lineSeparator());
            }
        }
        return msg;
    }
}
