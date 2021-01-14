/**
 *  Owntracks Connector Piston(v.0.0.1)
 *
 * MIT License
 *
 * Copyright (c) 2021 fison67@nate.com
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
    name: "OwnTracks Connector Piston",
    namespace: "fison67",
    author: "fison67",
    description: "A Connector between OwnTracks and ST",
    parent: "fison67:OwnTracks Connector",
    category: "My Apps",
    iconUrl: "https://is2-ssl.mzstatic.com/image/thumb/Purple115/v4/0a/81/ef/0a81ef94-c3f2-dfbf-d0d1-f7ccf566676e/AppIcon-1x_U007emarketing-0-0-GLES2_U002c0-512MB-sRGB-0-0-0-85-220-0-0-0-6.png/246x0w.jpg",
    iconX2Url: "https://is2-ssl.mzstatic.com/image/thumb/Purple115/v4/0a/81/ef/0a81ef94-c3f2-dfbf-d0d1-f7ccf566676e/AppIcon-1x_U007emarketing-0-0-GLES2_U002c0-512MB-sRGB-0-0-0-85-220-0-0-0-6.png/246x0w.jpg",
    iconX3Url: "https://is2-ssl.mzstatic.com/image/thumb/Purple115/v4/0a/81/ef/0a81ef94-c3f2-dfbf-d0d1-f7ccf566676e/AppIcon-1x_U007emarketing-0-0-GLES2_U002c0-512MB-sRGB-0-0-0-85-220-0-0-0-6.png/246x0w.jpg",
    oauth: true
)

preferences {
   page(name: "mainPage")
   page(name: "notifyPage")
   page(name: "switchPage")
}

def mainPage(){

	dynamicPage(name:"mainPage", title:"", install: true, uninstall: true, submitOnChange: true) {
    	
       	section() {
            paragraph "#1, #2 Beacon이 순차적으로 present 되면 in, 반대면 out."
       	}
        
        section {
            input(name: "sensor1", type: "capability.presenceSensor", title: "Beacon #1", required: true, multiple: false)
            input(name: "sensor2", type: "capability.presenceSensor", title: "Beacon #2", required: true, multiple: false)
            input(name: "devName", type: "text", title: "Label", defaultValue: "Virtual Device", required: true)
            paragraph "허브 선택"
            input(name: "devHub", type: "enum", title: "Hub", required: true, multiple: false, options: getHubs())
        }
        
       	section() {
            paragraph "선택 사항"
            href "notifyPage", title: "Notify Message Setting", description:""
            href "switchPage", title: "Switch On/Off Setting", description:""
       	}
    }
}

def notifyPage(){
	dynamicPage(name:"notifyPage", title:"", submitOnChange: true) {
    
       	section() {
            paragraph "In, Out 되었을 때 Notify Message Settings."
       	}
        
        section {
            input(name: "notifyDevices", type: "capability.speechSynthesis", title: "Notify", required: false, multiple: true)
            input(name: "notifyInText", type: "text", title: "In 내용", required: true)
            input(name: "notifyOutText", type: "text", title: "Out 내용", required: true)
        }
    }
}

def switchPage(){
	dynamicPage(name:"switchPage", title:"", submitOnChange: true) {
    
       	section() {
            paragraph "In, Out 되었을 때 Switch On/Off Settings."
       	}
        
        section {
            input(name: "switchDevices", type: "capability.switch", title: "Switch", required: false, multiple: true)
            input(name: "switchInDevices", type: "enum", title: "In", required: false, options: ["on", "off"])
            input(name: "switchOutDevices", type: "enum", title: "Out", required: false, options: ["on", "off"])
        }
    }
}

def getHubs(){
	def list = []
    location.getHubs().each { hub ->
    	list.push(hub.name)
    }
    return list
}

def getHubID(name){
	def id = null
    location.getHubs().each { hub ->
    	if(hub.name == name){
        	id = hub.id
        }
    }
    return id
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
    def dni = "owntracks-connector-virtual-" + UUID.randomUUID().toString()
    state.childDNI = dni
    def childDevice = addChildDevice("streamorange58819", "Owntrack InOut Sensor", dni, getHubID(devHub), [
        "label": devName
    ])    
    
    processChildStatus("out")
    
    state.lastUpdateTimeSensor1 = new Date().getTime()
    state.lastUpdateTimeSensor2 = new Date().getTime()
    
    state.lastProcessTimeSensor1 = 0
    state.lastProcessTimeSensor2 = 0
}

def uninstalled(){
	log.debug "uninstalled"
    unschedule()
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    unschedule()
    initialize()
}


def initialize() {
    app.updateLabel(devName)
    
    subscribe(sensor1, "presence", sensor1StateChangeHandler)
    subscribe(sensor2, "presence", sensor2StateChangeHandler)
}

def sensor1StateChangeHandler(event){
	if(event.value == "not present"){
    	return
    }
	log.debug "sensor1:: " + event.value
    
    def now = new Date().getTime()
    state.lastUpdateTimeSensor1 = now
    
    if(!_existSensor2Changed()){
        return
    }
	log.debug "sensor1:: process...."
    
    if(now > (state.lastUpdateTimeSensor2 as int)){
        processChildStatus("out")
        _updateLasetProcessTimeSensor2()
//       sensor2.sendEvent(name:"presence", value:"not present")
		log.debug "sensor1:: complete...."
    }
}

def sensor2StateChangeHandler(event){
	if(event.value == "not present"){
    	return
    }
	log.debug "sensor2:: " + event.value
    
    def now = new Date().getTime()
    state.lastUpdateTimeSensor2 = now
    
    if(!_existSensor1Changed()){
        return
    }
	log.debug "sensor2:: process...."
    
    if(now > (state.lastUpdateTimeSensor1 as int)){
        processChildStatus("in")
        _updateLasetProcessTimeSensor1()
//        sensor1.sendEvent(name:"presence", value:"not present")
		log.debug "sensor2:: complete...."
    }
}

def _getDNI(event){
	return event.getDevice().deviceNetworkId
}

def processChildStatus(value){
	getChildDevice(state.childDNI).setStatus(value)
    processStatus(value)
}

def processStatus(value){

    notifyDevices.each { device ->
    	device.speak(value == "in" ? notifyInText : notifyOutText)
    }
    
    switchDevices.each { device ->
    	log.debug "switch " + switchInDevices + ", value=" + value
        if(value == "in"){
            if(switchInDevices == "on"){
                device.on()
            }else{
                device.off()
            }
        }else{
        	log.debug "out"
            if(switchOutDevices == "on"){
        	log.debug "on"
                device.on()
            }else{
        	log.debug "off"
                device.off()
            }
        }
    }
}

def _existSensor1Changed(){
	return state.lastUpdateTimeSensor1 != state.lastProcessTimeSensor1
}

def _existSensor2Changed(){
	return state.lastUpdateTimeSensor2 != state.lastProcessTimeSensor2
}

def _updateLasetProcessTimeSensor1(){
    state.lastProcessTimeSensor1 = state.lastUpdateTimeSensor1
}

def _updateLasetProcessTimeSensor2(){
    state.lastProcessTimeSensor2 = state.lastUpdateTimeSensor2
}
