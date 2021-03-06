package study.book;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import study.CET.ManageCET;
import tool.NetworkException;
import tool.R;
import tool.ValidateException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created with Intellij IDEA.
 * User: WuHaoLin
 * Date: 14-1-29
 * Time: 上午10:06
 */

/**
 * 查看我以借书
 */
public class MyLib {
    private static final Logger log = LoggerFactory.getLogger(ManageCET.class);

    private static final String URL_Login = "http://202.114.34.15/reader/redr_verify.php";
    private static final String URL_MyLibBooks = "http://202.114.34.15/reader/book_lst.php";
    public static final String CookieName = "PHPSESSID";


    /**
     * 发生网络异常时抛出异常
     *
     * @param XH
     * @param MM
     * @return
     * @throws Exception
     */
    private static String getCookie(String XH, String MM) throws NetworkException {
        Connection connection = Jsoup.connect(URL_Login);
        connection.userAgent(R.USER_AGENT);
        connection.data("number", XH);
        connection.data("passwd", MM);
        connection.data("select", "cert_no");
        try {
            connection.post();
        } catch (IOException e) {
            log.error(Arrays.toString(e.getStackTrace()));
            throw new NetworkException("学校图书查询系统繁忙");
        }
        return connection.response().cookie(CookieName);
    }

    /**
     * 用帐号密码去图书馆获得我的书
     *
     * @param XH 学号
     * @param MM 密码
     * @return 如果没有借书返回的数量1,//返回的第0个元素的title值等于获得的cookies值,用于续借时,不可能返回null
     */
    public static List<MyLibBook> get(String XH, String MM) throws NetworkException, ValidateException {
        String cookie = getCookie(XH, MM);
        Connection connection = Jsoup.connect(URL_MyLibBooks);
        connection.userAgent(R.USER_AGENT);
        connection.cookie(CookieName, cookie);
        Document document;
        try {
            document = connection.get();
        } catch (IOException e) {
            log.error(Arrays.toString(e.getStackTrace()));
            throw new NetworkException("学校图书查询系统繁忙");
        }

        String location = document.location();
        if (!location.equals("http://202.114.34.15/reader/book_lst.php")) {
            throw new ValidateException("账号密码错误");
        }

        List<MyLibBook> re = new ArrayList<>();
        re.add(new MyLibBook().setTitle(cookie));//返回的第0个元素的title值等于获得的cookies值,用于续借时
        Element main_con = document.getElementsByClass("table_line").first();
        if (main_con == null) {
            return re;
        }
        Elements trs = main_con.getElementsByTag("tr");
        try {
            for (int i = 1; i < trs.size(); i++) {
                MyLibBook myLibBook = new MyLibBook();
                myLibBook.setIndex(i);
                Elements tds = trs.get(i).getElementsByTag("td");
                Element theA = tds.get(1).getElementsByTag("a").first();
                myLibBook.setTitle(theA.text());
                myLibBook.setBookInfoURL("http://202.114.34.15/reader/" + theA.attr("href"));
                myLibBook.setGetTime(tds.get(2).text());
                myLibBook.setBackTime(tds.get(3).text());
                myLibBook.setXJJavaScriptFunction(tds.last().getElementsByTag("input").attr("onclick"));
                re.add(myLibBook);
            }
        } catch (Exception ignored) {
        }
        return re;
    }

    /**
     * 续借书
     *
     * @param barcode
     * @param check
     * @param time
     * @return 执行续借请求的结果
     * @throws tool.NetworkException
     */
    public static String renew(String barcode, String check, String time, String cookie) throws NetworkException {
        Connection connection = Jsoup.connect("http://202.114.34.15/reader/ajax_renew.php");
        connection.userAgent(R.USER_AGENT);
        connection.cookie(CookieName, cookie);
        connection.data("bar_code", barcode);
        connection.data("check", check);
        connection.data("time", time);
        try {
            return connection.get().toString();
        } catch (IOException e) {
            throw new NetworkException("学校图书查询系统繁忙");
        }
    }

    /**
     * 判断该密码是否正确
     *
     * @param XH 学号
     * @param MM 密码
     * @return 是否正确
     */
    public static boolean passwordIsOk(String XH, String MM) {
        try {
            String cookie = getCookie(XH, MM);
            Connection connection = Jsoup.connect(URL_MyLibBooks);
            connection.userAgent(R.USER_AGENT);
            connection.cookie(CookieName, cookie);
            Document document = connection.get();
            String location = document.location();
            return location.equals("http://202.114.34.15/reader/book_lst.php");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 向图书馆荐购该图书
     *
     * @param bookName 书名
     * @param press    出版社
     * @param lan      书的语种   C=chinese,U=外文
     * @param XH       学号
     * @param MM       密码
     * @return 荐购提交是否成功
     * @throws tool.NetworkException
     */
    public static boolean JianGouBook(String bookName, String press, String lan, String XH, String MM) throws NetworkException {
        Connection connection = Jsoup.connect("http://202.114.34.15/asord/asord_redr.php");
        String cookie = getCookie(XH, MM);//获得用于验证身份的cookies
        connection.cookie(CookieName, cookie);
        connection.userAgent(R.USER_AGENT);
        connection.timeout(R.ConnectTimeout);
        connection.data("click_type", "commit");
        connection.data("title", bookName);
        connection.data("a_name", "学号:" + XH);
        connection.data("b_pub", press);
        connection.data("b_date", "");
        connection.data("b_type", lan);//C=chinese,U=
        connection.data("b_remark", "我没有在我校图书馆找到这本书,但是这本书很有价值哦!希望老师给大家带来这本书.");
        Document document;
        try {
            document = connection.get();
        } catch (IOException e) {
            log.error(Arrays.toString(e.getStackTrace()));
            throw new NetworkException("学校图书查询系统繁忙");
        }
        return document.getElementById("err").text().contains("成功");
    }

    public static class MyLibBook implements Comparable<MyLibBook> {
        String title;
        String getTime;
        String backTime;
        String XJJavaScriptFunction;
        String BookInfoURL;
        int index;

        public String getTitle() {
            return title;
        }

        public MyLibBook setTitle(String title) {
            this.title = title;
            return this;
        }

        public String getGetTime() {
            return getTime;
        }

        public void setGetTime(String getTime) {
            this.getTime = getTime;
        }

        public String getBackTime() {
            return backTime;
        }

        public void setBackTime(String backTime) {
            this.backTime = backTime;
        }

        public String getXJJavaScriptFunction() {
            return XJJavaScriptFunction;
        }

        public void setXJJavaScriptFunction(String XJJavaScriptFunction) {
            this.XJJavaScriptFunction = XJJavaScriptFunction;
        }

        @Override
        public int compareTo(MyLibBook o) {
            return this.backTime.compareTo(o.getBackTime());
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public String getBookInfoURL() {
            return BookInfoURL;
        }

        public void setBookInfoURL(String bookInfoURL) {
            BookInfoURL = bookInfoURL;
        }
    }

}
