package com.mmt.reflection;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mmt.pojo.common.AirlineDetails;
import com.mmt.pojo.search.AggregatedSearchResponse;
import com.mmt.pojo.search.SearchRequest;
import com.mmt.pojo.search.soa.AggregatedSearchResponseSOA;

public class App {
	private static Set<String> uniqueClassNames = new HashSet<>();
	
    public static void main( String[] args ) throws ClassNotFoundException {
    	int counter=0;
    	generateCode(AggregatedSearchResponse.class, counter);
    }
    public static void generateCode(Class<?> t, int counter) throws ClassNotFoundException{
    	if(uniqueClassNames.contains(t.getSimpleName())){
    		return;
    	} else{
    		uniqueClassNames.add(t.getSimpleName());
    	}
    	String line = "public "+t.getSimpleName()+"("+t.getSimpleName()+" input){\n";
    	try{
	    	Method [] methods = t.getDeclaredMethods();
	    	for(Method method : methods){
	    		Class<?> returnType = method.getReturnType();
	    		String methodName = method.getName();
	    		if(methodName.startsWith("get") || methodName.startsWith("is")){
	    			if(isPrimitive(returnType)){
	    				line += "\tthis."+methodName.replaceFirst("get", "set").replaceFirst("is", "set")+"(input."+methodName+"()"+");\n";
	    			}else if(isListOrSet(returnType)){
	    				ParameterizedType type = (ParameterizedType)method.getGenericReturnType();
	    				line+=nullCheckStart("input."+methodName+"()");
	    				line += handleListOrSet(type, returnType, "input."+methodName+"()", counter);
	    				line += "\t\tthis."+methodName.replaceFirst("get", "set").replaceFirst("is", "set")+"(copy"+counter+""+");\n";
	    				line+=nullCheckEnd();
	    			} else if(isMap(returnType)){
	    				line+=nullCheckStart("input."+methodName+"()");
	    				ParameterizedType type = (ParameterizedType)method.getGenericReturnType();
	    				line += handleMap(type, returnType, "input."+methodName+"()", counter);
	    				line += "\t\tthis."+methodName.replaceFirst("get", "set").replaceFirst("is", "set")+"(copy"+counter+""+");\n";
	    				line+=nullCheckEnd();
	    			}else if(returnType.getCanonicalName().startsWith("java.lang.Object")){
    					//throw new RuntimeException("Unhandled jdk class "+returnType.getTypeName());
    					System.out.println("#########Received Object, please handle it manually.##########" + returnType.getSimpleName());
    					line+= "\t\tthis."+methodName.replaceFirst("get", "set").replaceFirst("is", "set")+"(input."+methodName+"()"+");\n";
    				} else{
    					if(returnType.getCanonicalName().startsWith("java.")){
    						System.out.println("#########Received unknown class, please handle it manually.##########" + returnType.getTypeName()+" "+returnType.getSimpleName());
    						continue;
    						//throw new RuntimeException("Unhandled jdk class "+returnType.getTypeName());
    					}
	    				line+= nullCheckStart("input."+methodName+"()");
	    				line+= "\t\tthis."+methodName.replaceFirst("get", "set").replaceFirst("is", "set")+"(new "+returnType.getSimpleName()+"(input."+methodName+"())"+");\n";
	    				line+= nullCheckEnd();
	    				generateCode(returnType, counter);
	    			}
	    		}
	    	}
	    	line+="}\n";
	    	System.out.println(line);
    	} catch(Exception e){
    		System.out.println(line);
			System.out.println("Error in "+t +" "+counter);
    		e.printStackTrace();
    		throw e;
    	}
    }
    private static String handleCollection(Type paramType/*Method method*/, Class<?> returnType, String methodName, int counter)
			throws ClassNotFoundException {
    	if(isListOrSet(returnType)){
    		return handleListOrSet(paramType, returnType, methodName, counter);
    	} else if(isMap(returnType)){
    		return handleMap(paramType, returnType, methodName, counter);
    	} else if(returnType.getCanonicalName().startsWith("java.")){
			System.out.println("#########Received unknown class in handleCollection, please handle it manually.##########"+returnType.getCanonicalName());
			return "";
			//throw new RuntimeException("Unhandled jdk class "+returnType.getTypeName());
		} else{
    		throw new RuntimeException("Unknown jdk type "+returnType);
    	}
    }
    private static String handleMap(Type type, Class<?> returnType, String methodName, int counter)
			throws ClassNotFoundException {
		String line = "";
		String keyTypeName = "";
		String valueTypeName = "";
		Type valueSubType = null;
		if(type instanceof ParameterizedType){
			ParameterizedType paramType = (ParameterizedType)type;
			Type keyType = paramType.getActualTypeArguments()[0];
			keyTypeName = keyType.getTypeName();
			valueSubType = paramType.getActualTypeArguments()[1];
			valueTypeName = valueSubType.getTypeName();
		} else{
			valueTypeName = type.getTypeName();
		}
		//Class<?> keyTypeClass = Class.forName(keyTypeName.replaceAll("<.*>", ""));
		Class<?> valueTypeClass = Class.forName(valueTypeName.replaceAll("<.*>", ""));
		String typeSimpleName = valueTypeClass.getSimpleName();
		line+="\t\tMap<"+keyTypeName+","+valueTypeName+"> copy"+counter+" = new LinkedHashMap<>();\n";
		line+="\t\tfor(Map.Entry<"+keyTypeName+","+valueTypeName+"> val"+counter+" : "+methodName+".entrySet()){\n";
		if(isCollection(valueTypeClass)){
			line+=nullCheckStart("val"+counter);
			int curCounter = counter;
			line+=handleCollection(valueSubType, valueTypeClass, "val"+counter+".getValue()", ++counter);
			line+="\t\t\tcopy"+curCounter+".put(val"+curCounter+".getKey(),copy"+(counter)+");\n";
			line+=nullCheckEnd();
		} else if(!isPrimitive(valueTypeClass)){
			generateCode(valueTypeClass, counter);
			line+="\t\t\tcopy"+counter+".put(val"+counter+".getKey(),new "+typeSimpleName+"(val"+counter+".getValue()));\n";
		} else if(isPrimitive(valueTypeClass)){
			line+="\t\t\tcopy"+counter+".put(val"+counter+".getKey(),new "+typeSimpleName+"(val"+counter+".getValue()));\n";
		} else if(valueTypeClass.getCanonicalName().startsWith("java.")){
			System.out.println("#########Received unknown class in handleMap, please handle it manually.##########"+valueTypeClass.getCanonicalName());
			//line+="\t\t\tcopy"+counter+".put(val"+counter+".getKey(),val"+counter+".getValue());\n";
			return "";
			//throw new RuntimeException("Unhandled jdk class "+returnType.getTypeName());
		} else{
			System.out.println("#########Received unknown class in handleMap, please handle it manually.##########"+valueTypeClass.getCanonicalName());
			throw new RuntimeException("Unhandled jdk class "+returnType.getTypeName());
			//line+="\t\t\tcopy"+counter+".put(val"+counter+".getKey(),new "+typeSimpleName+"(val"+counter+".getValue()));\n";
		}
		line+="\t\t}\n";
		return line;
	}
	private static String handleListOrSet(Type type,Class<?> returnType, String methodName, int counter)
			throws ClassNotFoundException {
		String line = "";
		try{
			String typeName = "";
			Type subType = null;
			if(type instanceof ParameterizedType){
				ParameterizedType paramType = (ParameterizedType)type;
				subType = paramType.getActualTypeArguments()[0];
				typeName = subType.getTypeName();
			} else{
				typeName = type.getTypeName();
			}
			Class<?> genTypeClass = Class.forName(typeName.replaceAll("<.*>", ""));
			String typeSimpleName = genTypeClass.getSimpleName();
			if(isList(returnType)){
				line+="\t\tList<"+typeName+"> copy"+counter+" = new ArrayList<>();\n";
			} else{
				line+="\t\tSet<"+typeName+"> copy"+counter+" = new LinkedHashSet<>();\n";
			}
			line+="\t\tfor("+typeSimpleName+" val"+counter+" : "+methodName+"){\n";
			if(isCollection(genTypeClass)){
				line+=nullCheckStart("val"+counter);
				int curCounter = counter;
				line+=handleCollection(subType, genTypeClass, "val"+counter, ++counter);
				line+="\t\t\tcopy"+curCounter+".add(val"+counter+");\n";
				line+=nullCheckEnd();
			} else if(!isPrimitive(genTypeClass)){
				generateCode(genTypeClass, counter);
				line+="\t\t\tcopy"+counter+".add(new "+typeSimpleName+"(val"+counter+"));\n";
			} else if(isPrimitive(genTypeClass)){
				line+="\t\t\tcopy"+counter+".add(new "+typeSimpleName+"(val"+counter+"));\n";
			} else if(genTypeClass.getCanonicalName().startsWith("java.")){
				System.out.println("#########Received unknown class in handleListOrSet, please handle it manually.##########"+genTypeClass.getCanonicalName());
				//line+="\t\t\tcopy"+counter+".put(val"+counter+".getKey(),val"+counter+".getValue());\n";
				return "";
				//throw new RuntimeException("Unhandled jdk class "+returnType.getTypeName());
			}else{
				System.out.println("#########Received unknown class in handleListOrSet, please handle it manually.##########"+genTypeClass.getCanonicalName());
				//line+="\t\t\tcopy"+counter+".add(new "+typeSimpleName+"(val"+counter+"));\n";
				throw new RuntimeException("Unhandled jdk class "+returnType.getTypeName());
			}
			line+="\t\t}\n";
			return line;
		} catch(Exception e){
			System.out.println(line);
			System.out.println("Error in "+returnType+" "+type+" "+methodName+ " "+counter);
			e.printStackTrace();
			throw e;
		}
	}
	private static String nullCheckEnd() {
		return "\t}\n";
	}
	private static String nullCheckStart(String methodName) {
		return "\tif("+methodName+" != null){\n";
	}
	private static boolean isCollection(Class<?> returnType) {
    	if(List.class.isAssignableFrom(returnType)
    			|| Set.class.isAssignableFrom(returnType)
    			|| Map.class.isAssignableFrom(returnType)){
    		return true;
    	}
		return false;
	}
	private static boolean isListOrSet(Class<?> returnType) {
    	return List.class.isAssignableFrom(returnType)
    			|| Set.class.isAssignableFrom(returnType);
	}
    private static boolean isList(Class<?> returnType) {
    	return List.class.isAssignableFrom(returnType);
	}
    private static boolean isMap(Class<?> returnType) {
    	return Map.class.isAssignableFrom(returnType);
	}
	private static boolean isPrimitive(Class<?> returnType){
    	return Integer.class.isAssignableFrom(returnType) || int.class.isAssignableFrom(returnType)
    			|| Short.class.isAssignableFrom(returnType) || short.class.isAssignableFrom(returnType)
    			|| Float.class.isAssignableFrom(returnType) || float.class.isAssignableFrom(returnType)
    			|| Byte.class.isAssignableFrom(returnType) || byte.class.isAssignableFrom(returnType)
    			|| Double.class.isAssignableFrom(returnType) || double.class.isAssignableFrom(returnType)
    			|| Long.class.isAssignableFrom(returnType) || long.class.isAssignableFrom(returnType)
    			|| Boolean.class.isAssignableFrom(returnType) || boolean.class.isAssignableFrom(returnType)
    			|| Date.class.isAssignableFrom(returnType) || String.class.isAssignableFrom(returnType)
    			|| Character.class.isAssignableFrom(returnType) || char.class.isAssignableFrom(returnType)
    			|| BigDecimal.class.isAssignableFrom(returnType) || returnType.isEnum();
    }
    class Xyz{
		private boolean aa;
		//private AtomicInteger ai;
		private Map<String,List<AirlineDetails>> list;
		/*public Xyz(Xyz input){
			if(input.getList() != null){
				Map<java.lang.String,java.util.List<com.mmt.pojo.common.AirlineDetails>> copy0 = new LinkedHashMap<>();
				for(Map.Entry<java.lang.String,java.util.List<com.mmt.pojo.common.AirlineDetails>> val0 : input.getList().entrySet()){
					if(val0 != null){
						List<com.mmt.pojo.common.AirlineDetails> copy1 = new ArrayList<>();
						for(AirlineDetails val1 : val0.getValue()){
							copy1.add(new AirlineDetails(val1));
						}
							copy0.put(val0.getKey(),copy1);
					}
				}
			}
			this.setAa(input.isAa());
		}*/
		public Map<String,List<AirlineDetails>> getList() {
			return list;
		}

		public void setList(Map<String,List<AirlineDetails>> list) {
			this.list = list;
		}

		public boolean isAa() {
			return aa;
		}

		/*public AtomicInteger getAi() {
			return ai;
		}

		public void setAi(AtomicInteger ai) {
			this.ai = ai;
		}*/

		public void setAa(boolean aa) {
			this.aa = aa;
		}
	}
	class Abc{
		private int a;
		private Xyz xyz;
		public int getA() {
			return a;
		}

		public void setA(int a) {
			this.a = a;
		}

		public Xyz getXyz() {
			return xyz;
		}

		public void setXyz(Xyz xyz) {
			this.xyz = xyz;
		}
		
	}
	
}
