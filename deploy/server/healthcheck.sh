#!/bin/sh
set -eu
# eclipse-temurin 的基础镜像不保证带 curl/wget；用 JRE 自带 JShell 发起 HTTP 请求。
exec jshell -q <<'EOF'
import java.net.*;
var connection = (HttpURLConnection) new URL("http://127.0.0.1:8082/actuator/health/readiness").openConnection();
connection.setConnectTimeout(3000);
connection.setReadTimeout(3000);
var code = connection.getResponseCode();
if (code != 200) throw new IllegalStateException("readiness status " + code);
var body = new String(connection.getInputStream().readAllBytes());
if (!body.contains("\"status\":\"UP\"")) throw new IllegalStateException("readiness not UP");
System.out.println("UP");
/exit
EOF
