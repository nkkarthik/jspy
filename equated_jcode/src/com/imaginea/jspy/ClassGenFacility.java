package com.imaginea.jspy;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtNewMethod;
import javassist.NotFoundException;

import org.apache.commons.lang3.text.WordUtils;

public class ClassGenFacility {
	final static String PACKAGE = "com.imaginea.jspy";
	final static ClassPool pool = ClassPool.getDefault();

	public Class<?> generateClass(String className,
			Map<String, UnitType> vars) throws NotFoundException,
			CannotCompileException {
		CtClass cls = null;
		try {
			ClassPool.doPruning = false;
			
			
			cls = pool.makeClass(PACKAGE + "." + className);
			CtClass[] ifiles = new CtClass[1];
			ifiles[0] = pool.get("com.imaginea.jspy.Node");
			cls.setInterfaces(ifiles);

			CtClass ccObject = pool.get("java.lang.Object");
			for (Iterator<String> itr = vars.keySet().iterator(); itr.hasNext();) {
				String keyName = itr.next();
				// TODO relate types with UnitType
				CtField field = new CtField(ccObject, keyName, cls);
				cls.addField(field);
				cls.addMethod(CtNewMethod.setter("set"+WordUtils.capitalize(keyName), field));
				cls.addMethod(CtNewMethod.getter("get"+WordUtils.capitalize(keyName), field));
			}
			cls.defrost();
			cls.writeFile("./bin/");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return pool.toClass(cls);
	}

	public static void main(String[] args) {
		Map<String, UnitType> genMap = new HashMap<String, UnitType>();
		genMap.put("method", UnitType.METHOD);
		genMap.put("thread", UnitType.THREAD);
		genMap.put("className", UnitType.CLASS);
		genMap.put("args", UnitType.MULTI);

		try {
			ClassGenFacility ane = new ClassGenFacility();
			Class<?> c = ane.generateClass("MethodEntryNode", genMap);
			c.getDeclaredFields();
			c.getDeclaredMethods();
		} catch (NotFoundException e) {
			e.printStackTrace();
		} catch (CannotCompileException e) {
			e.printStackTrace();
		}
	}
}
