package com.matsuz.gdns;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Header;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


class Resolver {
    private static final OkHttpClient client;

    static {
        client = new OkHttpClient();
    }

    private Resolver() {
    }

    static Message resolve(Name name, int rrType, String edns) throws IOException {
    	


        // for debug
        System.out.printf("[DEBUG] %s %s %s\n",
                name.toString(), Type.string(rrType), edns);

        // compare with static map
        if (rrType == Type.A || rrType == Type.ANY || rrType == Type.AAAA || rrType == Type.PTR) {
            List<Map<String, Object>> configList;
            if (rrType == Type.A || rrType == Type.ANY)
                configList = Configuration.getInstance().getIPv4ConfigList();
            else if (rrType == Type.AAAA) configList = Configuration.getInstance().getIPv6ConfigList();
            else configList = Configuration.getInstance().getPtrConfigList();
            if (configList != null)
                for (Map<String, Object> entry : configList) {
                    String regex = entry.get("pattern") + "\\.?";
                    if (Pattern.matches(regex, name.toString())) {
                        Message response = new Message();
                        response.addRecord(Record.fromString(name,
                                rrType == Type.ANY ? Type.A : rrType
                                , DClass.IN, 0,
                                (String) entry.get("data"), Name.root
                        ), Section.ANSWER);
                        return response;
                    }
                }
        }

        // request google public dns
        Message response = new Message();
        ResolveResult result = edns == null ? fetch(name, rrType) : fetch(name, rrType, edns);
        Header respHeader = response.getHeader();
        try {
            addRecords(response, result.answer, Section.ANSWER);
            addRecords(response, result.authority, Section.AUTHORITY);
        } catch (TextParseException e) {
            // 返回 SERVFAIL
            respHeader.setRcode(Rcode.SERVFAIL);
            return response;
        }

        if (result.tc) respHeader.setFlag(Flags.TC);
        if (result.rd) respHeader.setFlag(Flags.RD);
        if (result.ra) respHeader.setFlag(Flags.RA);
        if (result.ad) respHeader.setFlag(Flags.AD);
        if (result.cd) respHeader.setFlag(Flags.CD);
        respHeader.setRcode(result.status);

        return response;
    }

    private static ResolveResult fetch(Name name, int rrType, String edns) throws IOException {
     	Proxy socksProxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 8002));
        String stringUrl = "https://1.1.1.1/dns-query?name="+name;
        URL url = new URL(stringUrl);
  
     	/*
    	HttpUrl.Builder urlBuilder = new HttpUrl.Builder();
        urlBuilder.scheme("https").host("dns.google.com").addPathSegments("/resolve")
                .addQueryParameter("name", name.toString())
                .addQueryParameter("type", Type.string(rrType));
        if (edns != null)
            urlBuilder.addQueryParameter("edns_client_subnet", edns);

        Request httpRequest = new Request.Builder()
                .url(urlBuilder.build())
                .build();
        Response httpResponse = client.newCall(httpRequest).execute();
  */
    	HttpURLConnection socksConnection  = (HttpURLConnection) url.openConnection(socksProxy);


 		socksConnection.setRequestProperty("accept", "application/dns-json");
		BufferedReader br;
		if (200 <= socksConnection.getResponseCode() && socksConnection.getResponseCode() <= 299) {
 		    br = new BufferedReader(new InputStreamReader(socksConnection.getInputStream()));
 		} else {
 		    br = new BufferedReader(new InputStreamReader(socksConnection.getErrorStream()));
 		}
        return ResolveResult.fromString(br.readLine());
    }

    private static ResolveResult fetch(Name name, int rrType) throws IOException {
        return fetch(name, rrType, null);
    }

    // TODO: TextParseException
    private static void addRecords(Message message, List<ResolveResult.Record> records, int section) throws IOException {
        if (records == null) return;

        for (ResolveResult.Record record : records) {
            Record rec;
            if (section == Section.QUESTION)
                rec = Record.newRecord(Name.fromString(record.name), record.type, DClass.IN);
            else
                try {
                    rec = Record.fromString(
                            Name.fromString(record.name), record.type, DClass.IN,
                            record.ttl == null ? 0 : record.ttl,
                            record.data, Name.root
                    );
                } catch (TextParseException e) {
                    if (record.type == Type.RRSIG) continue;    // 跳过 RRSIG
                    // TODO: LOG(record.data)
                    System.err.printf("Type: %s, Data: %s\n", Type.string(record.type), record.data);
                    throw new TextParseException(e.getMessage());
                }
            message.addRecord(rec, section);
        }
    }
}
