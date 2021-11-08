import Bootstrap.CustomHelpers;
import Bootstrap.DiffPage;
import Bootstrap.ProgramHelpers;
import Bootstrap.RequestStatistics;
import com.github.kevinsawicki.http.HttpRequest;

import java.util.ArrayList;
import java.util.List;

public class HostCollision implements Runnable {
    private ProgramHelpers programHelpers;

    private RequestStatistics requestStatistics;

    private List<List<String>> collisionSuccessList;

    private List<String> scanProtocols;
    private List<String> ipList;
    private List<String> hostList;

    public HostCollision(
            ProgramHelpers programHelpers,
            RequestStatistics requestStatistics,
            List<List<String>> collisionSuccessList,
            List<String> scanProtocols,
            List<String> ipList,
            List<String> hostList) {
        this.programHelpers = programHelpers;
        this.requestStatistics = requestStatistics;
        this.collisionSuccessList = collisionSuccessList;
        this.scanProtocols = scanProtocols;
        this.ipList = ipList;
        this.hostList = hostList;
    }

    @Override
    public void run() {
        for (String ip : ipList) {
            for (String protocol : scanProtocols) {
                try {
                    // 基础请求
                    HttpRequest baseRequest = programHelpers.sendHttpGetRequest(protocol, ip, "");

                    Integer baseRequestLength = baseRequest.contentLength();
                    String baseRequestLocation = baseRequest.location();
                    String baseRequestBody = getBody(baseRequest);
                    String baseRequestTitle = CustomHelpers.getBodyTitle(baseRequestBody);
                    String baseRequestBodyFormat = baseRequestBody.replace(ip, "");
                    String baseRequestContent = DiffPage.getFilteredPageContent(baseRequestBodyFormat);

                    // 绝对错误请求
                    HttpRequest errorHostRequest = programHelpers.sendHttpGetRequest(protocol, ip, programHelpers.getErrorHost());

                    Integer errorHostRequestLength = errorHostRequest.contentLength();
                    String errorHostRequestLocation = errorHostRequest.location();
                    String errorHostRequestBody = getBody(errorHostRequest);
                    String errorHostRequestTitle = CustomHelpers.getBodyTitle(errorHostRequestBody);
                    String errorHostRequestBodyFormat = errorHostRequestBody.replace(programHelpers.getErrorHost(), "");
                    String errorHostRequestContent = DiffPage.getFilteredPageContent(errorHostRequestBodyFormat);

                    // 请求长度判断
                    if (baseRequestLocation == null && baseRequestLength <= 0) {
                        requestStatistics.add("numOfRequest", hostList.size());
                        if (programHelpers.isOutputErrorLog()) {
                            String str = String.format("协议:%s, ip:%s, host:%s 该请求长度为:%s 有异常,不进行碰撞",
                                    protocol, ip, ip, baseRequestLength);
                            System.out.println(str);
                        }
                        continue;
                    }

                    if (errorHostRequestLocation == null && errorHostRequestLength <= 0) {
                        requestStatistics.add("numOfRequest", hostList.size());
                        if (programHelpers.isOutputErrorLog()) {
                            String str = String.format("协议:%s, ip:%s, host:%s 该请求长度为:%s 有异常,不进行碰撞",
                                    protocol, ip, programHelpers.getErrorHost(), errorHostRequestLength);
                            System.out.println(str);
                        }
                        continue;
                    }

                    for (String host : hostList) {
                        requestStatistics.add("numOfRequest", 1);

                        // 正式进行host碰撞
                        try {
                            // 真正的碰撞请求
                            HttpRequest newRequest = programHelpers.sendHttpGetRequest(protocol, ip, host);

                            Integer newRequestLength = newRequest.contentLength();
                            String newRequestLocation = newRequest.location();
                            String newRequestBody = getBody(newRequest);
                            String newRequestTitle = CustomHelpers.getBodyTitle(newRequestBody);
                            String newRequestBodyFormat = newRequestBody.replace(host, "");
                            String newRequestContent = DiffPage.getFilteredPageContent(newRequestBodyFormat);

                            // 相对错误请求
                            HttpRequest newRequest2 = programHelpers.sendHttpGetRequest(protocol, ip, programHelpers.getRelativeHostName() + host);

                            Integer newRequest2Length = newRequest2.contentLength();
                            String newRequest2Location = newRequest2.location();
                            String newRequest2Body = getBody(newRequest2);
                            String newRequest2Title = CustomHelpers.getBodyTitle(newRequest2Body);
                            String newRequest2BodyFormat = newRequest2Body.replace(host, "");
                            String newRequest2Content = DiffPage.getFilteredPageContent(newRequest2BodyFormat);

                            // 请求长度判断
                            if (newRequestLocation == null && newRequestLength <= 0) {
                                if (programHelpers.isOutputErrorLog()) {
                                    String str = String.format("协议:%s, ip:%s, host:%s 该请求长度为:%s 有异常,不进行碰撞",
                                            protocol, ip, host, newRequestLength);
                                    System.out.println(str);
                                }
                                continue;
                            }
                            if (newRequest2Location == null && newRequest2Length <= 0) {
                                if (programHelpers.isOutputErrorLog()) {
                                    String str = String.format("协议:%s, ip:%s, host:%s 该请求长度为:%s 有异常,不进行碰撞",
                                            protocol, ip, host, newRequest2Length);
                                    System.out.println(str);
                                }
                                continue;
                            }

                            // 进行简单的内容匹配
                            if (newRequestContent.contains(baseRequestContent) ||
                                    newRequestContent.contains(errorHostRequestContent) ||
                                    newRequestContent.contains(newRequest2Content) ||
                                    newRequest2Content.contains(newRequestContent)) {
                                if (programHelpers.isOutputErrorLog()) {
                                    String str = String.format("协议:%s, ip:%s, host:%s 匹配失败", protocol, ip, host);
                                    System.out.println(str);
                                }
                                continue;
                            }

                            // title比对
                            if (newRequestTitle.trim().length() > 0) {
                                if (newRequest2Title.equals(newRequestTitle) ||
                                        baseRequestTitle.equals(newRequestTitle) ||
                                        errorHostRequestTitle.equals(newRequestTitle)
                                ) {
                                    if (programHelpers.isOutputErrorLog()) {
                                        String str = String.format("协议:%s, ip:%s, host:%s 匹配失败", protocol, ip, host);
                                        System.out.println(str);
                                    }
                                    continue;
                                }
                            }

                            // 相似度匹配
                            double htmlSimilarityRatio1 = DiffPage.getRatio(baseRequestBodyFormat, newRequestBodyFormat);
                            double htmlSimilarityRatio2 = DiffPage.getRatio(errorHostRequestBodyFormat, newRequestBodyFormat);
                            double htmlSimilarityRatio3 = DiffPage.getRatio(newRequest2BodyFormat, newRequestBodyFormat);
                            if (htmlSimilarityRatio1 >= programHelpers.getSimilarityRatio() ||
                                    htmlSimilarityRatio2 >= programHelpers.getSimilarityRatio() ||
                                    htmlSimilarityRatio3 >= programHelpers.getSimilarityRatio()) {
                                if (programHelpers.isOutputErrorLog()) {
                                    String str = String.format("协议:%s, ip:%s, host:%s 匹配失败", protocol, ip, host);
                                    System.out.println(str);
                                }
                                continue;
                            }

                            if (httpStatusCodeCheck(String.valueOf(newRequest.code()))) {
                                // host碰撞成功的数据写入
                                List<String> data = new ArrayList<>();
                                data.add(protocol);
                                data.add(ip);
                                data.add(host);
                                data.add(newRequestTitle);

                                data.add(String.valueOf(newRequestLength));
                                data.add(String.valueOf(baseRequestLength));
                                data.add(String.valueOf(errorHostRequestLength));
                                data.add(String.valueOf(newRequest2Length));

                                data.add(String.valueOf(newRequest.code()));
                                data.add(String.valueOf(baseRequest.code()));
                                data.add(String.valueOf(errorHostRequest.code()));
                                data.add(String.valueOf(newRequest2.code()));

                                // 保存host碰撞成功的数据
                                collisionSuccessList.add(data);

                                // 实时输出host碰撞成功的日志数据
                                String successLog = String.format(
                                        "协议:%s, ip:%s, host:%s, title:%s, 匹配成功的数据包大小:%s, 状态码:%s 匹配成功",
                                        protocol, ip, host, newRequestTitle, newRequestLength, newRequest.code());
                                System.out.println(successLog);
                            } else {
                                if (programHelpers.isOutputErrorLog()) {
                                    String str = String.format("协议:%s, ip:%s, host:%s, title:%s, 匹配成功的数据包大小:%s, 状态码:%s 不是白名单状态码,忽略处理",
                                            protocol, ip, host, newRequestTitle, newRequestLength, newRequest.code());
                                    System.out.println(str);
                                }
                            }
                        } catch (HttpRequest.HttpRequestException hre) {
                            if (programHelpers.isOutputErrorLog()) {
                                String str = String.format("协议:%s, ip:%s, host:%s 匹配失败", protocol, ip, host);
                                System.out.println(str);
                            }
                            continue;
                        }
                    }
                } catch (HttpRequest.HttpRequestException hre) {
                    requestStatistics.add("numOfRequest", hostList.size());
                    if (programHelpers.isOutputErrorLog()) {
                        String str = String.format("error: 站点 %s 访问失败,不进行host碰撞", protocol + ip);
                        System.out.println(str);
                    }
                }
            }
        }
    }

    /**
     * http状态码检查
     *
     * @param str
     * @return Boolean
     */
    private Boolean httpStatusCodeCheck(String str) {
        List<String> collisionSuccessStatusCode = programHelpers.getCollisionSuccessStatusCode();
        if (collisionSuccessStatusCode.size() == 0) {
            return true;
        }

        for (String cssc : collisionSuccessStatusCode) {
            if (cssc.equals(str)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 获取请求body
     *
     * @param request
     * @return String
     */
    private String getBody(HttpRequest request) {
        String body;
        String requestLocation = request.location();
        Integer requestLength = request.contentLength();

        if (requestLocation != null && requestLength <= 0) {
            body = requestLocation;
        } else {
            body = request.body();
        }

        return body;
    }
}