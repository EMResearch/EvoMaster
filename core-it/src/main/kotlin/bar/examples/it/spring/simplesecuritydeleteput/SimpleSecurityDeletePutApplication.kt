package bar.examples.it.spring.simplesecuritydeleteput

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.neo4j.Neo4jProperties.Authentication
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@SpringBootApplication(exclude = [SecurityAutoConfiguration::class])
@RequestMapping(path = ["/api"])
@RestController
open class SimpleSecurityDeletePutApplication {

    private val endpointMap = HashMap<Int, String>()

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplication.run(SimpleSecurityDeletePutApplication::class.java, *args)
        }
    }

    // get for one endpoint
    @GetMapping("/endpoint/{endpointIdentifier}")
    open fun getForEndpoint(@PathVariable endpointIdentifier: Int) : ResponseEntity<String> {

        return ResponseEntity.status(201).body("endpoint_GET : $endpointIdentifier")
    }

    // put for one endpoint
    @PutMapping("/endpoint/{endpointIdentifier}")
    open fun putForEndpoint(@PathVariable endpointIdentifier: Int,
                            @RequestParam newParam : String,
                            auth : Authentication) : ResponseEntity<String> {

        // no authentication

        // 200 for resource modification
        if (endpointIdentifier in endpointMap) {
            endpointMap.set(endpointIdentifier, newParam)
            return ResponseEntity.status(200).build()
        }
        // 201 for resource creation
        else {
            endpointMap.put(endpointIdentifier, newParam)
            return ResponseEntity.status(201).build()
        }
    }


    // delete for one endpoint with authenticated user
    @DeleteMapping("/endpoint/{endpointIdentifier}")
    open fun deleteForEndpoint(@PathVariable endpointIdentifier: Int,
                            @RequestParam newParam : String,
                            auth : Authentication) : ResponseEntity<String> {

        if(auth.username.equals("creator")) {
            endpointMap.remove(endpointIdentifier)
            return ResponseEntity.status(200).build()
        }
        else {
            return ResponseEntity.status(403).build()
        }

    }

}