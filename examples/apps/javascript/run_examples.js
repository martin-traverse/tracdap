/*
 * Licensed to the Fintech Open Source Foundation (FINOS) under one or
 * more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * FINOS licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


// To run these examples outside of a browser, XMLHttpRequest and WebSocket are required
import xhr2 from 'xhr2';
import WebSocket from "ws";
global.XMLHttpRequest = xhr2.XMLHttpRequest;
global.WebSocket = WebSocket;

// Node supplies fetch since Node 18
// If it is missing, try to import it from the node-fetch package
if (!global.hasOwnProperty("fetch")) {
    import("node-fetch").then(_fetch => {global.fetch = _fetch;})
}


const ALL_EXAMPLES = [
    "hello_world",
    "metadata_mojo",
    "add_resources",
    "using_data",
    "arrow",
    "streaming",
    "error_handling"
]

async function runExample(exampleName) {

    console.log("Running example: " + exampleName);

    const module = `./src/${exampleName}.js`
    const example = await import(module);

    await example.main();
}

(async () => {

    try {

        const examples = process.argv.length > 2
            ? process.argv.slice(2)
            : ALL_EXAMPLES;

        for (let i = 0; i < examples.length; i++) {

            const exampleName = examples[i];
            await runExample(exampleName);
        }
    }
    catch (err) {

        if (err.hasOwnProperty("message"))
            console.log(err.message);
        else
            console.log(JSON.stringify(err));

        // Ensure errors escape so the process is marked as failed
        throw err;
    }

})()
