package webserver;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Map;

import db.DataBase;
import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HttpRequestUtils;
import util.IOUtils;

public class RequestHandler extends Thread {
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private Socket connection;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());

        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line = br.readLine();
            log.debug("request lne : {}",line);
            if(line == null){
                return;
            }

            String[] httpInfos = line.split(" ");
            boolean logined = false;
            int contentLen = 0;
            while(!line.equals("")){
                line = br.readLine();
                if(line.contains("Content-Length")){
                    contentLen = getContentLength(line);
                } else if (line.contains("Cookie:")) {
                    logined = checkLogin(line);
                }
               log.debug("header : {}", line);
            }

            DataOutputStream dos = new DataOutputStream(out);
            String url = httpInfos[1];
            byte[] body;
            if(url.startsWith("/user/create")){
                Map<String, String> params = HttpRequestUtils.parseQueryString(IOUtils.readData(br, contentLen));
                User user = new User(params.get("userId"), params.get("password"), params.get("name"), params.get("email"));
                log.debug("Join User: {}", user);
                DataBase.addUser(user);
                response302Header(dos, "/index.html");
            } else if (url.startsWith("/user/login")) {
                Map<String, String> params = HttpRequestUtils.parseQueryString(IOUtils.readData(br, contentLen));
                User user = DataBase.findUserById(params.get("userId"));
                log.debug("Login User: {}", user);
                if(user == null){
                    responseResource(out,"/user/login_failed.html");
                    return;
                }
                if(user.getPassword().equals(params.get("password"))){
                    response302LoginSuccessHeader(dos);
                }else{
                    responseResource(out,"/user/login_failed.html");
                }
            } else if(url.startsWith("/user/list")){
                if(!logined){
                    responseResource(out,"/user/login.html");
                    return;
                }
                Collection<User> users = DataBase.findAll();
                StringBuilder sb = new StringBuilder();
                sb.append("<table border='1'>");
                for(User user : users){
                    sb.append("<tr>");
                    sb.append("<td>"+user.getUserId()+"</td>");
                    sb.append("<td>"+user.getName()+"</td>");
                    sb.append("<td>"+user.getEmail()+"</td>");
                    sb.append("</tr>");
                }
                sb.append("</table>");
                body = sb.toString().getBytes();
                response200Header(dos, body.length);
                responseBody(dos, body);
            } else if(url.endsWith(".css")){
                body = Files.readAllBytes(new File("./webapp" +  url).toPath());
                response200CssHeader(dos, body.length);
                responseBody(dos, body);
            }
            else{
                responseResource(out, url);
            }

        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response200CssHeader(DataOutputStream dos, int length) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/css \r\n");
            dos.writeBytes("Content-Length: "+ length +"\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private boolean checkLogin(String line) {
        Map<String, String> cookie = HttpRequestUtils.parseCookies(getHeaderValue(line));
        String value = cookie.get("logined");
        if(value == null){
            return false;
        }
        return Boolean.parseBoolean(value);
    }

    private String getHeaderValue(String line) {
        String[] header = line.split(":");
        return header[1].trim();
    }

    private void response302LoginSuccessHeader(DataOutputStream dos) {
        try {
            dos.writeBytes("HTTP/1.1 302 Redirect \r\n");
            dos.writeBytes("Set-Cookie: logined=true\r\n");
            dos.writeBytes("Location: /index.html\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseResource(OutputStream out, String url) throws IOException {
        DataOutputStream dos =  new DataOutputStream(out);
        byte[] body = Files.readAllBytes(new File("./webapp"+url).toPath());
        response200Header(dos, body.length);
        responseBody(dos, body);
    }

    private int getContentLength(String line) {
        return Integer.parseInt(getHeaderValue(line));
    }

    private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response302Header(DataOutputStream dos, String url) {
        try {
            dos.writeBytes("HTTP/1.1 302 Redirect \r\n");
            dos.writeBytes("Location: "+url+"\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}
