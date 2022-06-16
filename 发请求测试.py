import json
import time

import httpx

nt = int(str(time.time()).replace('.', '')[:13])
url = f"https://wmapi-mt.meituan.com/mtapi/v6/home/feeds/tabs?wm_appversion=11.20.205&utm_medium=android&wmUuidDeregistration=0&wm_seq=42&uuid=0000000000000553D587C99A040E298B69D8D1BF421DFA165427618326859910&wm_ctype=mtandroid&personalized=1&platform=4&wm_latitude=36862776&poilist_mt_cityid=224&wm_actual_longitude=119391875&content_personalized_switch=2&ad_personalized_switch=2&wm_visitid=1832c296-d2f7-4dfb-a233-00c75f8acf97&wm_dversion=30_11&push_token=dpsh4519399654cb7fea24ede036beb024cbatpu&app=0&poilist_wm_cityid=370700&wm_longitude=119391875&utm_campaign=AgroupBgroupC0E0Ghomepage_category1_394__a1__c-1024&wm_actual_latitude=36862776&wm_pwh=1&ci=527&f=android&wmUserIdDeregistration=0&waimai_sign=maDMjkajD%2F6XXDNNKO9KX3kr3H45X9SfpRQ0qzSX2naKZKFtgHaG7IfTgNyEMjBYdPquw3HDs6ih%0AFXcPJ%2F%2BA%2F%2Fd1%2BQwczYyHSbF6NWivCaQ%2BFsVvW8UQnRMWvxuacrmB4BApR6MjovRo7aQQ9m%2Fh2o26%0AFPkb9SFUE8VcP1OAtew%3D%0A&version=11.20.205&req_time=1654317095095&utm_term=1100200205&app_model=0&wm_dtype=Pixel&wm_uuid=0000000000000553D587C99A040E298B69D8D1BF421DFA165427618326859910&partner=4&utm_source=qq&version_name=11.20.205&utm_content=553d587c99a040e298b69d8d1bf421dfa165427618326859910&msid=553d587c99a040e298b69d8d1bf421dfa1654276183268599101654279224697&userid=-1&p_appid=10&__reqTraceID=199e89c7-9b48-4133-8a2e-92beae60df0e"

mtgsig_url="http://127.0.0.1:9999/api/meituan/do-work"
params="refresh_type=0&load_type=1&rank_list_id=d5705395fff046aab578df7a5082dbe7&session_id=e3fbfe1b-5fe7-46ca-b5e8-3080b1f38b4a1654317025425140&seq_num=0&net_stat=0"
mtgsig_params ={"url":url,"params":params,"method":"post"}
mtgsig=httpx.post(mtgsig_url,json=mtgsig_params).json()
print(json.dumps(mtgsig))
headers = {
    "mtgsig": f"{json.dumps(mtgsig)}",
    "retrofit_exec_time": f'{nt}', "request-belong": "com.meituan.android.pt.homepage.activity.MainActivity",
    "pragma-os": "MApi 1.1 com.sankuai.meituan 11.20.205 qq Pixel; Android 11",

    "User-Agent": "AiMeiTuan /google-11-Pixel-1794-1080-420-11.20.205-1100200205-553d587c99a040e298b69d8d1bf421dfa165427618326859910-qq",
    "Accept-Encoding": "gzip, deflate", "userid": "-1", "Content-Type": "application/x-www-form-urlencoded",
    "Connection": "close"}
resp = httpx.post(url, headers=headers, content=params)
print(resp.text)

###
# 这是美团的请求逻辑，这里面的是可以跑通的
###
