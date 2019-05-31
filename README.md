# MuWire - Easy Anonymous File-Sharing

MuWire is an easy to use file-sharing program which offers anonymity using [I2P technology](http://geti2p.net).

It is inspired by the LimeWire Gnutella client and developped by a former LimeWire developer.

The project is in development.  You can find technical documentation in the "doc" folder.

### Building

You need Gradle and a JDK 8 or newer.  After installing those and setting up the appropriate paths, just type

```
gradle assemble 
```

If you want to run the unit tests, type
```
gradle build
```

Some of the UI tests will fail because they haven't been written yet :-/

### Running

You need to have an I2P router up and running on the same machine.  After you build the application, look inside "gui/build/distributions".  Untar/unzip one of the "shadow" files and then run the jar contained inside.  

The first time you run MuWire it will ask you to select a nickname.  This nickname will be displayed with search results, so that others can verify the file was shared by you.

To share files, edit $HOME/.MuWire/MuWire.properties and add the property "sharedFiles" with value a comma-separated list of absolute paths to shared files or folders.

At the moment there are very few nodes on the network, so you will see very few connections and search results.  It is best to leave MuWire running all the time, just like I2P.


### Known bugs and limitations

* Any shared files get re-hashed on startup
* Sometimes the list of shared files gets lost 
* Many UI features you would expect are not there yet


