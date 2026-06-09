package com.tikectsystem.config;

import cn.hutool.core.date.DateTime;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 自定义json序列化
 * @author: 阿星不是程序员
 **/
public class JsonCustomSerializer extends BeanSerializerModifier {

	private static final JsonSerializer<Object> EMPTY_STRING_NULL_SERIALIZER = new JsonSerializer<Object>() {
		@Override
		public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
			gen.writeString("");
		}
	};

	private static final JsonSerializer<Object> FALSE_NULL_SERIALIZER = new JsonSerializer<Object>() {
		@Override
		public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
			gen.writeBoolean(false);
		}
	};

	private static final JsonSerializer<Object> EMPTY_ARRAY_NULL_SERIALIZER = new JsonSerializer<Object>() {
		@Override
		public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
			gen.writeStartArray();
			gen.writeEndArray();
		}
	};

	@Override
	public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc,
			List<BeanPropertyWriter> beanProperties) {
		for (BeanPropertyWriter writer : beanProperties) {
			JsonSerializer<Object> js = judgeType(writer);
			if (js != null) {
				writer.assignNullSerializer(js);
			}
		}
		return beanProperties;
	}

	public JsonSerializer<Object> judgeType(BeanPropertyWriter writer) {
		JavaType javaType = writer.getType();
		Class<?> clazz = javaType.getRawClass();
		if (String.class.isAssignableFrom(clazz)) {
			return EMPTY_STRING_NULL_SERIALIZER;
		}
		if (Number.class.isAssignableFrom(clazz)) {
			return EMPTY_STRING_NULL_SERIALIZER;
		}
		if (Boolean.class.isAssignableFrom(clazz)) {
			return FALSE_NULL_SERIALIZER;
		}
		if (java.util.Date.class.isAssignableFrom(clazz)) {
			return EMPTY_STRING_NULL_SERIALIZER;
		}
		if (clazz.equals(DateTime.class)) {
			return EMPTY_STRING_NULL_SERIALIZER;
		}
		if (clazz.isArray() || List.class.isAssignableFrom(clazz) || Set.class.isAssignableFrom(clazz)) {
			return EMPTY_ARRAY_NULL_SERIALIZER;
		}
		return null;
	}
}
