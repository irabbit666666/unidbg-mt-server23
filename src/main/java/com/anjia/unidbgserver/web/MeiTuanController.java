package com.anjia.unidbgserver.web;

import com.anjia.unidbgserver.service.MeiTuanServiceWorker;
import lombok.SneakyThrows;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * MVC控制类
 *
 * @author AnJia
 */
@RestController
@RequestMapping(path = "/api/meituan", produces = MediaType.APPLICATION_JSON_VALUE)
public class MeiTuanController {

    @Resource(name = "meiTuanServiceWorker")
    private MeiTuanServiceWorker meiTuanServiceWorker;

    /**
     * 获取 meituan 计算结果
     *
     * public byte[] ttEncrypt(@RequestParam(required = false) String key1, @RequestBody String body)
     * 这是接收一个url参数，名为key1,接收一个post或者put请求的body参数
     * key1是选填参数，不写也不报错，值为,body只有在请求方法是POST时才有，GET没有
     *
     * @return 结果
     */

    @SneakyThrows @RequestMapping(value = "do-work", method = RequestMethod.POST)
    public Object meituan(@RequestBody MeiTuanForm meiTuanForm) {
        System.out.println(meiTuanForm);
        return meiTuanServiceWorker.doWork(meiTuanForm).get();
    }
}
