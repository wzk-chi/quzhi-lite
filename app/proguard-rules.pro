# Gson reads these application models reflectively for API responses and local storage.
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-keep class com.quzhi.lite.data.** { *; }

# Paho MQTT creates its logger through Class.forName() during MqttAsyncClient
# construction. Keep the implementation name in the minified release build.
-keep class org.eclipse.paho.client.mqttv3.logging.** { *; }
