 /**
 *  OwnTracks Sensor (v.0.0.2)
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

metadata {
	definition (name: "OwnTracks Sensor", namespace: "fison67", author: "fison67") {
		capability "Presence Sensor"
      	capability "Sensor"
        
        attribute "address", "string"
        attribute "lastCheckin", "Date"
         
        command "setStatus"
	}

	simulator {}
    
    preferences {
        input name: "baseAddressCheck", title:"Base Address #1" , type: "enum", required: true, options: ["on", "off"], defaultValue: "off"
        input name: "baseAddress1", title:"Base Address #1 (경기도, 서울특별시)" , type: "string", required: false
        input name: "baseAddress2", title:"Base Address #2 (안양시)" , type: "string", required: false
        input name: "baseAddress3", title:"Base Address #3 (만안구, 중구)" , type: "string", required: false
        input name: "baseAddress4", title:"Base Address #4 (안양동, 태평로1가)" , type: "string", required: false
    }

	tiles {
		multiAttributeTile(name:"presence", type: "generic", width: 6, height: 4){
			tileAttribute ("device.presence", key: "PRIMARY_CONTROL") {
               	attributeState "not present", label:'${name}', backgroundColor: "#ffffff", icon:"st.presence.tile.presence-default" 
            	attributeState "present", label:'present', backgroundColor: "#53a7c0", icon:"st.presence.tile.presence-default" 
			}
            
            tileAttribute("device.lastCheckin", key: "SECONDARY_CONTROL") {
    			attributeState("default", label:'\nLast Update: ${currentValue}')
            }
		}
        
        valueTile("lastPresnce_label", "", decoration: "flat") {
            state "default", label:'Last\nIn'
        }
        valueTile("lastPresnce", "device.lastPresnce", decoration: "flat", width: 3, height: 1) {
            state "default", label:'${currentValue}'
        }
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:""
        }
        valueTile("lastNotPresnce_label", "", decoration: "flat") {
            state "default", label:'Last\nOut'
        }
        valueTile("lastNotPresnce", "device.lastNotPresnce", decoration: "flat", width: 3, height: 1) {
            state "default", label:'${currentValue}'
        }
        valueTile("empty_label", "", decoration: "flat", width:1, height: 1) {
            state "default", label:''
        }
        valueTile("address", "device.address", decoration: "flat", width: 5, height: 1) {
            state "default", label:'${currentValue}'
        }
	}

}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
}

def setStatus(jsonObj){
    log.debug jsonObj
    
    def now = new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone)
    
    def type = jsonObj.topic.split("/")[3]
    switch(type){
    case "event":
        checkAddress(jsonObj.event, jsonObj.lat, jsonObj.lon)
    	break
    }
    
    sendEvent(name: "lastCheckin", value: now, displayed: false)
}

def setAPIKey(key){
	state.apiKey = key
}

def checkAddress(event, lat, lon){
	if(state.apiKey != null && state.apiKey != ""){
        def params = [
            uri: "https://maps.googleapis.com/maps/api/geocode/json?latlng=${lat},${lon}&language=ko&key=${state.apiKey}"
        ]
        try {
            httpGet(params) { resp ->
                def address = resp.data.results[0].formatted_address
                def addressOK = true
                def errMessage = ""
                if(baseAddressCheck == "on"){
                    def list = resp.data.results[0].address_components
                    list.each { item ->
                        if(item.types.contains("administrative_area_level_1")){
							if(baseAddress1 != "" && item.short_name != baseAddress1){
                            	addressOK = false
                                errMessage = "Base#1 ${baseAddress1} / ${item.short_name}" 
                            }
                        }else if(item.types.contains("locality")){
							if(baseAddress2 != "" && item.short_name != baseAddress2){
                            	addressOK = false
                                errMessage = errMessage + "\nBase#2 ${baseAddress2} / ${item.short_name}" 
                            }
                        }else if(item.types.contains("sublocality_level_1")){
							if(baseAddress3 != "" && item.short_name != baseAddress3){
                            	addressOK = false
                                errMessage = errMessage + "\nBase#3 ${baseAddress3} / ${item.short_name}" 
                            }
                        }else if(item.types.contains("sublocality_level_2")){
							if(baseAddress4 != ""){
                        		def _baseAddr = baseAddress4.split(",")
                                def _subAddrOK = false
                                _baseAddr.each{ _addr->
                                	if(item.short_name.indexOf(_addr) > -1){
                                    	_subAddrOK = true
                                    }
                                }
                            	addressOK = _subAddrOK
                                errMessage = errMessage + "\nBase#4 ${baseAddress4} / ${item.short_name}" 
                            }
                        }
                    }
                    log.debug "Address check >> ${addressOK}"
                }
                
    			sendEvent(name: "address", value: address)
                if(addressOK){
                	setCurrentValue(event)
                }else{
                	state.errCount = state.errCount + 1
                    if(state.errCount == 100){
                        state.errCount = 0
                    }
                    def msg = "Address is wrong!!! ${errMessage} #${state.errCount}"
                    log.error msg
                    sendEvent(name: "errorAddress", value: msg)
                }
            }
        } catch (e) {
            log.error "something went wrong: $e"
        }
    }else{
    	setCurrentValue(event)
    }
    
}

def setCurrentValue(event){
    def now = new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone)
	if(device.currentValue("presence") == "not present" && event == "enter"){
        sendEvent(name: "lastPresnce", value: now, displayed: false )
    }else if(device.currentValue("presence") == "present" && event == "leave"){
        sendEvent(name: "lastNotPresnce", value: now, displayed: false )
    }
    sendEvent(name: "presence", value: event == "enter" ? "present" : "not present")
}

def updated() {
	state.errCount = 0
}
