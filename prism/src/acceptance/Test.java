package acceptance;

import acceptance.AcceptanceParity.Objective;
import acceptance.AcceptanceParity.Parity;

public class Test
{

	public static void main(String[] args)
	{
		AcceptanceParity ap = new AcceptanceParity(Objective.MIN, Parity.ODD, 0);
		ap.outputHOAHeader(System.out);
		ap = new AcceptanceParity(Objective.MIN, Parity.ODD, 1);
		ap.outputHOAHeader(System.out);
		ap = new AcceptanceParity(Objective.MIN, Parity.ODD, 2);
		ap.outputHOAHeader(System.out);
		ap = new AcceptanceParity(Objective.MIN, Parity.ODD, 3);
		ap.outputHOAHeader(System.out);
		ap = new AcceptanceParity(Objective.MIN, Parity.ODD, 4);
		ap.outputHOAHeader(System.out);
		ap = new AcceptanceParity(Objective.MIN, Parity.ODD, 5);
		ap.outputHOAHeader(System.out);

		ap = new AcceptanceParity(Objective.MIN, Parity.EVEN, 0);
		ap.outputHOAHeader(System.out);
		ap = new AcceptanceParity(Objective.MIN, Parity.EVEN, 1);
		ap.outputHOAHeader(System.out);
		ap = new AcceptanceParity(Objective.MIN, Parity.EVEN, 5);
		ap.outputHOAHeader(System.out);

		ap = new AcceptanceParity(Objective.MAX, Parity.ODD, 0);
		ap.outputHOAHeader(System.out);
		ap = new AcceptanceParity(Objective.MAX, Parity.ODD, 1);
		ap.outputHOAHeader(System.out);
		ap = new AcceptanceParity(Objective.MAX, Parity.ODD, 2);
		ap.outputHOAHeader(System.out);
		ap = new AcceptanceParity(Objective.MAX, Parity.ODD, 3);
		ap.outputHOAHeader(System.out);
		ap = new AcceptanceParity(Objective.MAX, Parity.ODD, 4);
		ap.outputHOAHeader(System.out);
		ap = new AcceptanceParity(Objective.MAX, Parity.ODD, 5);
		ap.outputHOAHeader(System.out);
	}

}
