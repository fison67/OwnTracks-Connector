/**
 *  Owntracks Connector (v.0.0.2)
 *
 * MIT License
 *
 * Copyright (c) 2019 fison67@nate.com
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */
 
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

definition(
    name: "OwnTracks Connector",
    namespace: "fison67",
    author: "fison67",
    description: "A Connector between OwnTracks and ST",
    category: "My Apps",
    iconUrl: "https://is2-ssl.mzstatic.com/image/thumb/Purple115/v4/0a/81/ef/0a81ef94-c3f2-dfbf-d0d1-f7ccf566676e/AppIcon-1x_U007emarketing-0-0-GLES2_U002c0-512MB-sRGB-0-0-0-85-220-0-0-0-6.png/246x0w.jpg",
    iconX2Url: "https://is2-ssl.mzstatic.com/image/thumb/Purple115/v4/0a/81/ef/0a81ef94-c3f2-dfbf-d0d1-f7ccf566676e/AppIcon-1x_U007emarketing-0-0-GLES2_U002c0-512MB-sRGB-0-0-0-85-220-0-0-0-6.png/246x0w.jpg",
    iconX3Url: "https://is2-ssl.mzstatic.com/image/thumb/Purple115/v4/0a/81/ef/0a81ef94-c3f2-dfbf-d0d1-f7ccf566676e/AppIcon-1x_U007emarketing-0-0-GLES2_U002c0-512MB-sRGB-0-0-0-85-220-0-0-0-6.png/246x0w.jpg",
    oauth: true
)

preferences {
   page(name: "mainPage")
   page(name: "addPage")
   page(name: "addManualPage")
   page(name: "addManualCompletePage")
   page(name: "registeredPage")
}

def mainPage() {
	state.workMode = "main"
    state.added = ""
	dynamicPage(name: "mainPage", title: "OwnTracks", nextPage: null, uninstall: true, install: true) {
       	section() {
            paragraph "OwnTrack Http URL"
            href "addPage", title:"ADD Device", description:""
            href "addManualPage", title:"ADD Manual Device", description:""
            href url:"${apiServerUrl("/api/smartapps/installations/${app.id}/config?access_token=${state.accessToken}")}", style:"embedded", required:false, title:"Config", description:"Copy this text to Owntracks"
       	}
        section("Configure Google GeoCoding API Key"){
           input "googleKey", "string", title: "Google GeoCoding API Key", required: false
       }
    }
}

def addPage(){
	state.workMode = "find"
    if(state.added == ""){
        dynamicPage(name:"addPage", title:"Finding....", refreshInterval:3) {
            section("Press Publish Settings on your owntraks app. That device will be registered automatically.") {
                paragraph "Waiting..."
            }
        }
    }else{
    	def tmp = state.added.split("/")
    	dynamicPage(name:"registeredPage", title:"This device is registered.") {
            section("Complete.") {
                paragraph "USER ID: ${tmp[1]}, DEVICE ID: ${tmp[2]}"
            }
        }
    }
}


def addManualPage(){
	dynamicPage(name: "addManualPage", title:"Type a ID", nextPage:"addManualCompletePage") {
    	section ("Select") {
        	input(name: "manualID", type: "number", title: "Type", description: null, multiple: false, required: true, submitOnChange: true)
        }
    }
}

def addManualCompletePage(){
	addDevice(manualID)
	dynamicPage(name:"addManualCompletePage", title:"This device is registered.", nextPage: "mainPage") {
        section("Complete.") {
            paragraph "Complete ID: ${manualID}"
        }
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    
    if (!state.accessToken) {
        createAccessToken()
    }
    
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"

    initialize()
}

def initialize() {
    state.workMode = "nothing"
    
    def list = getChildDevices()
    list.each { child ->
        try{
            child.setAPIKey(settings.googleKey)
        }catch(e){
        }
    }
}

def addDevice(id){
	try{
        def dni = "owntracks-connector-${id}"
        def chlid = getChildDevice(dni)
        if(!chlid){
            def childDevice = addChildDevice("fison67", "OwnTracks Sensor", dni, getLocationID(), [
                "label": "OwnTracks Manual"
            ])
        }
    }catch(err){
    	log.error(err)
    }
}

def updateDevice(){
	try{
        def data = request.JSON
        def id = data.tid
        log.debug data
        def topic = data.topic
        def tmp = topic.split('/')
        def userID = tmp[1]
        def type = tmp[3]
        if(type == "dump" && state.workMode == "find"){
        	log.debug "ADD device"
            state.added = topic
            
            def dni = "owntracks-connector-${data.configuration.tid}"
            def chlid = getChildDevice(dni)
            if(!child){
                def childDevice = addChildDevice("fison67", "OwnTracks Sensor", dni, getLocationID(), [
                    "label": "OwnTracks " + userID
                ])
				state.workMode = ""
            }
            
        }else{
        	def dni = "owntracks-connector-" + id
            def chlid = getChildDevice(dni)
            if(chlid){
                chlid.setStatus(data)
            }else{
                log.debug "No child"
            }
        }
        
        def resultString = new groovy.json.JsonOutput().toJson("result":true)
        render contentType: "application/javascript", data: resultString
    }catch(err){
    	def resultString = new groovy.json.JsonOutput().toJson("result":false)
        render contentType: "application/javascript", data: resultString
    }
}

def getLocationID(){
	def locationID = null
    try{ locationID = location.hubs[0].id }catch(err){}
    return locationID
}

def authError() {
    [error: "Permission denied"]
}

def renderConfig() {
    def url = ( apiServerUrl("/api/smartapps/installations/") + app.id + "/update?access_token=" + state.accessToken )
    render contentType: "text/plain", data: url
}

mappings {
    if (!params.access_token || (params.access_token && params.access_token != state.accessToken)) {
        path("/config")                         { action: [GET: "authError"] }
        path("/update")                         { action: [POST: "authError"]  }

    } else {
        path("/config")                         { action: [GET: "renderConfig"]  }
        path("/update")                         { action: [POST: "updateDevice"]  }
    }
}
