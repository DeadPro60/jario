package jario.snes;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Properties;

public class Main
{
	public static void main(String[] args)
	{
//		try
//		{
//			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
//		}
//		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e)
//		{
//			e.printStackTrace();
//		}
		
//		new SnesSystem();
		
		try
		{
        	File dir = new File("components"+File.separator);
    		File file = new File("components.properties");
    		ClassLoader loader = Main.class.getClassLoader();
			Properties prop = new Properties();
			try
			{
				if (dir.exists() && dir.listFiles().length > 0)
				{
					File[] files = dir.listFiles();
					URL[] urls = new URL[files.length];
					for (int i=0; i<files.length; i++) urls[i] = files[i].toURI().toURL();
					loader = new URLClassLoader(urls, Main.class.getClassLoader());
				}
				URL url = file.exists() ? file.toURI().toURL() : loader.getResource("resources"+File.separator+"components.properties");
				if (url != null) prop.load(url.openStream());
			}
			catch (IOException e) { }
    		
			Class.forName(prop.getProperty("SYSTEM", "SYSTEM"), true, loader).newInstance();
		}
		catch (InstantiationException | IllegalAccessException | ClassNotFoundException e)
		{
			e.printStackTrace();
		}
	}
}