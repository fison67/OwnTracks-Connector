 /**
 *  OwnTracks Sensor (v.0.0.1)
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
        input name: "baseAddress", title:"Base Address" , type: "string", required: false
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
    	def event = jsonObj.event
        checkAddress(event, jsonObj.lat, jsonObj.lon)
        if(baseAddress == null || baseAddress == ""){
            setCurrentValue(event)
        }
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
    			sendEvent(name: "address", value: address)
                if(baseAddress != null && baseAddress != ""){
                	if(address.indexOf(baseAddress) > -1){
                		setCurrentValue(event)
                    }else{
                    	state.errCount = state.errCount + 1
                        if(state.errCount == 100){
                        	state.errCount = 0
                        }
                    	def msg = "Address is wrong!!! Base Address: ${baseAddress}, called Address: ${address} #${state.errCount}"
                    	log.error msg
                        sendEvent(name: "errorAddress", value: msg)
                    }
                }else{
                }
            }
        } catch (e) {
            log.error "something went wrong: $e"
        }
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
