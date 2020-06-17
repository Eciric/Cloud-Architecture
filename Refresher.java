package JavaProject;

import javax.swing.JFrame;

public class Refresher extends Thread
{
	JFrame window;
	
	Refresher(JFrame w){window = w;}
	
	public void run()
	{
		while (true)
		{
			window.revalidate();
			window.repaint();
			try
			{
				Thread.sleep(600);
			}
			catch (Exception e)
			{
				e.printStackTrace();
				break;
			}
		}
	}
}