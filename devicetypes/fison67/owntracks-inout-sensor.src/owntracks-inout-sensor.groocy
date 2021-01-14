/**
 *  Owntrack Presence Sensor (v.0.0.1)
 */
 
import groovy.json.JsonSlurper

metadata {
	definition (name: "Owntrack InOut Sensor", namespace: "streamorange58819", author: "fison67", vid: "d57303bd-a91a-3366-ae14-acf96f997f1c") {
		capability "streamorange58819.inout"
        capability "Presence Sensor"
		capability "Sensor"
	}
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
}

def updated() {}

def instaelld(){
	sendEvent(name: "lastInOut", value:"in")
	sendEvent(name: "presence", value:"present")
}

def setStatus(value){
	sendEvent(name: "lastInOut", value:value)
	sendEvent(name: "presence", value:value == "in" ? "present" : "not present")
}
