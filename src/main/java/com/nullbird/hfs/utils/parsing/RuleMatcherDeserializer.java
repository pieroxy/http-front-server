package com.nullbird.hfs.utils.parsing;

import com.google.gson.*;
import com.nullbird.hfs.config.rules.RuleMatcher;
import com.nullbird.hfs.config.rules.matchers.*;

import java.lang.reflect.Type;

public class RuleMatcherDeserializer  implements JsonDeserializer<RuleMatcher> {
  @Override
  public RuleMatcher deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
    JsonObject jObject = (JsonObject) jsonElement;
    JsonElement typeObj = jObject.get("type");

    if(typeObj!= null ){
      String nodeType = typeObj.getAsString();

      switch (nodeType){
        case "URLRegexpMatcher":
          return jsonDeserializationContext.deserialize(jsonElement, URLRegexpMatcher.class);
        case "URLSubstringMatcher":
          return jsonDeserializationContext.deserialize(jsonElement, URLSubstringMatcher.class);
        case "HostIs":
          return jsonDeserializationContext.deserialize(jsonElement, HostIs.class);
        case "ContainsCookie":
          return jsonDeserializationContext.deserialize(jsonElement, ContainsCookie.class);
        case "And":
          return jsonDeserializationContext.deserialize(jsonElement, And.class);
        case "Not":
          return jsonDeserializationContext.deserialize(jsonElement, Not.class);
        case "Or":
          return jsonDeserializationContext.deserialize(jsonElement, Or.class);
        case "All":
          return jsonDeserializationContext.deserialize(jsonElement, All.class);
        default:
          throw new RuntimeException("Unknown rule matcher node type " + nodeType);
      }
    } else {
      throw new RuntimeException("Rule matcher node has no 'type' attribute");
    }
  }
}